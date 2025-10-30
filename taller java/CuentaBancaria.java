import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Clase principal que modela una Cuenta Bancaria.
 * Incluye lógica de operaciones (depósito, retiro, transferencia, intereses),
 * además de un pequeño sistema de consola (CLI) para interacción manual.
 */
public class CuentaBancaria {
    // Generador de IDs únicos para cada cuenta
    private static final AtomicInteger SEQ = new AtomicInteger(1);

    /** Tipo de cuenta: puede ser CORRIENTE o AHORROS */
    public enum TipoCuenta { CORRIENTE, AHORROS }

    /** Tipos de transacción posibles */
    public static class Transaccion {
        public enum Tipo {
            DEPOSITO, RETIRO, TRANSFERENCIA_ENTRANTE, TRANSFERENCIA_SALIENTE, INTERES, CARGO
        }

        private final Tipo tipo;
        private final double monto;
        private final double saldoResultante;
        private final LocalDateTime fechaHora;

        public Transaccion(Tipo tipo, double monto, double saldoResultante) {
            this.tipo = tipo;
            this.monto = monto;
            this.saldoResultante = saldoResultante;
            this.fechaHora = LocalDateTime.now();
        }

        @Override
        public String toString() {
            return String.format("[%s] %-25s Monto: %.2f | Saldo: %.2f",
                    fechaHora, tipo, monto, saldoResultante);
        }
    }

    // ==== ATRIBUTOS DE INSTANCIA ====
    private final int id;
    private final String cliente;
    private final TipoCuenta tipo;
    private double saldo;
    private final List<Transaccion> historial = new ArrayList<>();

    // ==== CONSTRUCTOR ====
    public CuentaBancaria(String cliente, TipoCuenta tipo, double saldoInicial) {
        this.id = SEQ.getAndIncrement();
        this.cliente = Objects.requireNonNull(cliente, "Cliente no puede ser null");
        this.tipo = Objects.requireNonNull(tipo, "Tipo de cuenta no puede ser null");
        this.saldo = Math.max(0.0, saldoInicial);
        registrarTransaccion(Transaccion.Tipo.DEPOSITO, saldoInicial); // registrar saldo inicial
    }

    // ==== GETTERS ====
    public int getId() { return id; }
    public String getCliente() { return cliente; }
    public TipoCuenta getTipo() { return tipo; }

    public synchronized double getSaldo() { return saldo; }

    // ==== HISTORIAL ====
    private synchronized void registrarTransaccion(Transaccion.Tipo tipo, double monto) {
        historial.add(new Transaccion(tipo, monto, saldo));
    }

    /** Devuelve una copia inmodificable del historial */
    public synchronized List<Transaccion> getHistorial() {
        return Collections.unmodifiableList(new ArrayList<>(historial));
    }

    // ==== OPERACIONES BÁSICAS ====

    /** Depositar dinero en la cuenta */
    public synchronized void depositar(double cantidad) {
        if (cantidad <= 0) throw new IllegalArgumentException("La cantidad a depositar debe ser mayor que 0");
        saldo += cantidad;
        registrarTransaccion(Transaccion.Tipo.DEPOSITO, cantidad);
    }

    /** Retirar dinero de la cuenta (lanza excepción si no hay fondos) */
    public synchronized void retirar(double cantidad) throws InsufficientFundsException {
        if (cantidad <= 0) throw new IllegalArgumentException("La cantidad a retirar debe ser mayor que 0");
        if (cantidad > saldo) throw new InsufficientFundsException("Saldo insuficiente");
        saldo -= cantidad;
        registrarTransaccion(Transaccion.Tipo.RETIRO, -cantidad);
    }

    /** Aplica interés solo si la cuenta es de tipo AHORROS */
    public synchronized void aplicarInteres(double tasa) {
        if (tipo != TipoCuenta.AHORROS) return;
        if (tasa <= 0) throw new IllegalArgumentException("La tasa debe ser positiva.");
        double interes = saldo * tasa;
        saldo += interes;
        registrarTransaccion(Transaccion.Tipo.INTERES, interes);
    }

    /** Aplica un cargo mensual solo si la cuenta es CORRIENTE */
    public synchronized void aplicarCargoMensual(double monto) throws InsufficientFundsException {
        if (tipo != TipoCuenta.CORRIENTE) return;
        if (monto <= 0) throw new IllegalArgumentException("Monto del cargo debe ser positivo.");
        if (monto > saldo) throw new InsufficientFundsException("Saldo insuficiente para aplicar el cargo.");
        saldo -= monto;
        registrarTransaccion(Transaccion.Tipo.CARGO, -monto);
    }

    @Override
    public String toString() {
        return String.format("ID:%d - %s (%s) - Saldo: %.2f", id, cliente, tipo, saldo);
    }

    // ==== EXCEPCIÓN PERSONALIZADA ====
    public static class InsufficientFundsException extends Exception {
        public InsufficientFundsException(String msg) { super(msg); }
    }

    // ==== CLASE INTERNA BANCO ====
    /** 
     * Representa un "repositorio" de cuentas bancarias.
     * Maneja la creación, búsqueda, listados, transferencias y operaciones globales.
     */
    static class Banco {
        private final Map<Integer, CuentaBancaria> cuentas = new LinkedHashMap<>();

        public CuentaBancaria crearCuenta(String cliente, TipoCuenta tipo, double saldoInicial) {
            CuentaBancaria c = new CuentaBancaria(cliente, tipo, saldoInicial);
            cuentas.put(c.getId(), c);
            return c;
        }

        public Optional<CuentaBancaria> obtenerCuenta(int id) {
            return Optional.ofNullable(cuentas.get(id));
        }

        public Collection<CuentaBancaria> listar() {
            return Collections.unmodifiableCollection(cuentas.values());
        }

        /** 
         * Transfiere dinero entre dos cuentas.
         * Si ocurre algún error, no modifica ningún saldo (operación atómica).
         */
        public synchronized void transferir(int fromId, int toId, double monto)
                throws IllegalArgumentException, InsufficientFundsException {

            if (fromId == toId) throw new IllegalArgumentException("No puede transferirse a la misma cuenta.");
            if (monto <= 0) throw new IllegalArgumentException("Monto de transferencia inválido.");

            CuentaBancaria origen = cuentas.get(fromId);
            CuentaBancaria destino = cuentas.get(toId);

            if (origen == null || destino == null)
                throw new IllegalArgumentException("Cuenta origen o destino no encontrada.");

            // Se intenta retirar y depositar de forma atómica
            synchronized (this) {
                origen.retirar(monto); // lanza excepción si no hay fondos
                destino.depositar(monto);
                origen.registrarTransaccion(Transaccion.Tipo.TRANSFERENCIA_SALIENTE, -monto);
                destino.registrarTransaccion(Transaccion.Tipo.TRANSFERENCIA_ENTRANTE, monto);
            }
        }

        /** 
         * Aplica intereses a cuentas de ahorro y cargos a cuentas corrientes.
         * Se usa una tasa (por ejemplo 0.05 = 5%) y un monto fijo de cargo.
         */
        public void aplicarInteresesYCargos(double tasaAhorros, double cargoCorriente) {
            for (CuentaBancaria c : cuentas.values()) {
                try {
                    if (c.getTipo() == TipoCuenta.AHORROS)
                        c.aplicarInteres(tasaAhorros);
                    else
                        c.aplicarCargoMensual(cargoCorriente);
                } catch (Exception e) {
                    System.out.println("No se pudo aplicar a cuenta " + c.getId() + ": " + e.getMessage());
                }
            }
        }
    }

    // ==== INTERFAZ DE CONSOLA ====
    public static void main(String[] args) {
        Banco banco = new Banco();
        

        try (Scanner sc = new Scanner(System.in)) {
            while (true) {
                System.out.println("\n********************");
                System.out.println("1 - Crear cuenta");
                System.out.println("2 - Consultar saldo");
                System.out.println("3 - Retirar");
                System.out.println("4 - Depositar");
                System.out.println("5 - Listar cuentas");
                System.out.println("6 - Transferir entre cuentas");
                System.out.println("7 - Ver historial de cuenta");
                System.out.println("8 - Aplicar intereses/cargos globales");
                System.out.println("9 - Salir");
                System.out.print("Seleccione opción: ");

                String linea = sc.nextLine().trim();
                if (linea.isEmpty()) continue;

                int opcion;
                try { opcion = Integer.parseInt(linea); }
                catch (NumberFormatException e) { System.out.println("Opción inválida."); continue; }

                try {
                    switch (opcion) {
                        case 1 -> crearCuentaFlow(sc, banco);
                        case 2 -> consultarSaldoFlow(sc, banco);
                        case 3 -> retirarFlow(sc, banco);
                        case 4 -> depositarFlow(sc, banco);
                        case 5 -> listarFlow(banco);
                        case 6 -> transferirFlow(sc, banco);
                        case 7 -> historialFlow(sc, banco);
                        case 8 -> aplicarInteresesFlow(sc, banco);
                        case 9 -> { System.out.println("Saliendo..."); return; }
                        default -> System.out.println("Opción no válida.");
                    }
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }
        }
    }

    // ==== FLUJOS DE MENÚ ====
    private static void crearCuentaFlow(Scanner sc, Banco banco) {
        System.out.print("Nombre del titular: ");
        String nombre = sc.nextLine().trim();
        if (nombre.isEmpty()) { System.out.println("Nombre no puede estar vacío."); return; }

        System.out.print("Tipo (1=Corriente, 2=Ahorros): ");
        String t = sc.nextLine().trim();
        TipoCuenta tipo = "2".equals(t) ? TipoCuenta.AHORROS : TipoCuenta.CORRIENTE;

        System.out.print("Saldo inicial: ");
        double saldoInicial;
        try { saldoInicial = Double.parseDouble(sc.nextLine().trim()); }
        catch (NumberFormatException e) { System.out.println("Saldo inválido."); return; }

        CuentaBancaria c = banco.crearCuenta(nombre, tipo, saldoInicial);
        System.out.println("Cuenta creada: " + c);
    }

    private static void consultarSaldoFlow(Scanner sc, Banco banco) {
        obtenerCuentaPorId(sc, banco).ifPresentOrElse(
            c -> System.out.println("Saldo: " + c.getSaldo()),
            () -> System.out.println("Cuenta no encontrada.")
        );
    }

    private static void retirarFlow(Scanner sc, Banco banco) {
        Optional<CuentaBancaria> o = obtenerCuentaPorId(sc, banco);
        if (o.isEmpty()) { System.out.println("Cuenta no encontrada."); return; }
        CuentaBancaria c = o.get();

        System.out.print("Cantidad a retirar: ");
        double monto;
        try { monto = Double.parseDouble(sc.nextLine().trim()); }
        catch (NumberFormatException e) { System.out.println("Monto inválido."); return; }

        try {
            c.retirar(monto);
            System.out.println("Retiro exitoso. Nuevo saldo: " + c.getSaldo());
        } catch (InsufficientFundsException e) {
            System.out.println("Operación fallida: " + e.getMessage());
        }
    }

    private static void depositarFlow(Scanner sc, Banco banco) {
        Optional<CuentaBancaria> o = obtenerCuentaPorId(sc, banco);
        if (o.isEmpty()) { System.out.println("Cuenta no encontrada."); return; }
        CuentaBancaria c = o.get();

        System.out.print("Cantidad a depositar: ");
        double monto;
        try { monto = Double.parseDouble(sc.nextLine().trim()); }
        catch (NumberFormatException e) { System.out.println("Monto inválido."); return; }

        c.depositar(monto);
        System.out.println("Depósito exitoso. Nuevo saldo: " + c.getSaldo());
    }

    private static void listarFlow(Banco banco) {
        Collection<CuentaBancaria> cuentas = banco.listar();
        if (cuentas.isEmpty()) { System.out.println("No hay cuentas."); return; }
        cuentas.forEach(System.out::println);
    }

    private static void transferirFlow(Scanner sc, Banco banco) {
        try {
            System.out.print("ID de cuenta origen: ");
            int fromId = Integer.parseInt(sc.nextLine().trim());
            System.out.print("ID de cuenta destino: ");
            int toId = Integer.parseInt(sc.nextLine().trim());
            System.out.print("Monto a transferir: ");
            double monto = Double.parseDouble(sc.nextLine().trim());

            banco.transferir(fromId, toId, monto);
            System.out.println("Transferencia realizada exitosamente.");
        } catch (NumberFormatException e) {
            System.out.println("Entrada inválida.");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void historialFlow(Scanner sc, Banco banco) {
        Optional<CuentaBancaria> o = obtenerCuentaPorId(sc, banco);
        if (o.isEmpty()) { System.out.println("Cuenta no encontrada."); return; }
        CuentaBancaria c = o.get();

        System.out.println("=== Historial de cuenta " + c.getId() + " ===");
        c.getHistorial().forEach(System.out::println);
    }

    private static void aplicarInteresesFlow(Scanner sc, Banco banco) {
        try {
            System.out.print("Tasa de interés para ahorros (ej: 0.05): ");
            double tasa = Double.parseDouble(sc.nextLine().trim());
            System.out.print("Cargo mensual para cuentas corrientes: ");
            double cargo = Double.parseDouble(sc.nextLine().trim());

            banco.aplicarInteresesYCargos(tasa, cargo);
            System.out.println("Intereses y cargos aplicados correctamente.");
        } catch (NumberFormatException e) {
            System.out.println("Entrada inválida.");
        }
    }

    private static Optional<CuentaBancaria> obtenerCuentaPorId(Scanner sc, Banco banco) {
        System.out.print("Ingrese ID de cuenta: ");
        try {
            int id = Integer.parseInt(sc.nextLine().trim());
            return banco.obtenerCuenta(id);
        } catch (NumberFormatException e) {
            System.out.println("ID inválido.");
            return Optional.empty();
        }
    }
}
