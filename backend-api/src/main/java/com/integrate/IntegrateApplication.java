package com.integrate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del monolito modular Intégrate (ADR-0002).
 * Ubicado en el paquete raíz para que el escaneo de componentes cubra
 * {@code common}, {@code config}, {@code security} y {@code modules.*}.
 */
@SpringBootApplication
public class IntegrateApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntegrateApplication.class, args);
    }
}
