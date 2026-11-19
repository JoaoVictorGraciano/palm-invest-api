package com.palminvest.api.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Vencimento do DARF: último dia útil do mês seguinte, pulando fim de semana e dias sem expediente bancário nacional
class CalendarioBancarioTest {

    @Test
    void pascoaPeloCalendarioGregoriano() {
        assertEquals(LocalDate.of(2024, 3, 31), CalendarioBancario.pascoa(2024));
        assertEquals(LocalDate.of(2025, 4, 20), CalendarioBancario.pascoa(2025));
        assertEquals(LocalDate.of(2017, 4, 16), CalendarioBancario.pascoa(2017));
    }

    @Test
    void ultimoDiaUtilPulaFimDeSemanaEFeriados() {
        assertEquals(LocalDate.of(2025, 4, 30), CalendarioBancario.ultimoDiaUtil(YearMonth.of(2025, 4)));  // quarta comum
        assertEquals(LocalDate.of(2025, 5, 30), CalendarioBancario.ultimoDiaUtil(YearMonth.of(2025, 5)));  // 31 é sábado
        assertEquals(LocalDate.of(2024, 3, 28), CalendarioBancario.ultimoDiaUtil(YearMonth.of(2024, 3)));  // 29 é Sexta-feira Santa
        assertEquals(LocalDate.of(2017, 2, 24), CalendarioBancario.ultimoDiaUtil(YearMonth.of(2017, 2)));  // 27 e 28 são Carnaval
        assertEquals(LocalDate.of(2026, 12, 30), CalendarioBancario.ultimoDiaUtil(YearMonth.of(2026, 12))); // 31/12 sem expediente
    }

    @Test
    void vencimentoDoDarfEhNoMesSeguinte() {
        assertEquals(LocalDate.of(2024, 3, 28), RegrasDeImpostoDeRenda.vencimentoDarf(YearMonth.of(2024, 2)));
        assertEquals(LocalDate.of(2026, 1, 30), RegrasDeImpostoDeRenda.vencimentoDarf(YearMonth.of(2025, 12))); // 31/01/2026 é sábado
    }
}
