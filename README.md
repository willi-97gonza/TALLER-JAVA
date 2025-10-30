# 💰 Sistema de Gestión de Cuentas Bancarias (Java CLI)

Este proyecto implementa un **sistema de gestión bancaria** completamente funcional en **Java**, que permite crear cuentas, realizar depósitos, retiros, transferencias, aplicar intereses o cargos mensuales, y consultar historiales de transacciones desde una interfaz **de consola (CLI)**.

---

## 🧩 Descripción General

La aplicación modela el comportamiento básico de un **banco** y de sus **cuentas bancarias**, ofreciendo una simulación realista de operaciones financieras comunes.  

Cuenta con:
- **Gestión de cuentas** (crear, listar, consultar saldo).
- **Operaciones básicas**: depósito, retiro y transferencias entre cuentas.
- **Cálculo automático de intereses** (para cuentas de ahorro).
- **Aplicación de cargos mensuales** (para cuentas corrientes).
- **Historial de transacciones** con sello de tiempo.
- **Interfaz de consola interactiva** para ejecución directa.

---

## 🧱 Estructura del Código

El proyecto está contenido en una sola clase principal `CuentaBancaria`, pero internamente se organiza en múltiples componentes lógicos:

### 🔹 `CuentaBancaria`
Representa una cuenta individual de un cliente:
- Atributos: `id`, `cliente`, `tipo`, `saldo`, `historial`.
- Métodos principales:
  - `depositar(double cantidad)`
  - `retirar(double cantidad)`
  - `aplicarInteres(double tasa)`
  - `aplicarCargoMensual(double monto)`
  - `getHistorial()`

### 🔹 `Banco` (clase interna estática)
Administra el conjunto de cuentas:
- Permite crear, buscar y listar cuentas.
- Gestiona **transferencias entre cuentas**.
- Aplica **intereses y cargos mensuales** de forma masiva.

### 🔹 `Transaccion` (clase interna estática)
Registra cada operación realizada con:
- Tipo de transacción (depósito, retiro, transferencia, etc.)
- Monto involucrado.
- Saldo resultante.
- Fecha y hora exactas (`LocalDateTime`).

### 🔹 `InsufficientFundsException`
Excepción personalizada que se lanza cuando se intenta retirar o transferir un monto mayor al saldo disponible.

---

## ⚙️ Tecnologías Utilizadas

- **Lenguaje:** Java 17+  
- **Librerías estándar:**  
  - `java.util` (colecciones, scanner, objetos)
  - `java.time` (fechas y horas)
  - `java.util.concurrent.atomic.AtomicInteger` (generador de IDs únicos)
- **Interfaz:** Línea de comandos (CLI)

---




