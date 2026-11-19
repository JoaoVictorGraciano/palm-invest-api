package com.palminvest.api.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;
import java.util.Set;

/**
 * Dias úteis bancários, para o vencimento do DARF (último dia útil do mês seguinte ao da apuração).
 * Considera fins de semana e os dias sem expediente bancário nacional: feriados nacionais (Lei 662/1949,
 * Lei 6.802/1980, Lei 14.759/2023), Carnaval, Sexta-feira Santa, Corpus Christi e 31/12.
 * Não considera feriados estaduais ou municipais.
 */
final class CalendarioBancario {

    private static final Set<MonthDay> FERIADOS_FIXOS = Set.of(
            MonthDay.of(1, 1),   // Confraternização Universal
            MonthDay.of(4, 21),  // Tiradentes
            MonthDay.of(5, 1),   // Dia do Trabalho
            MonthDay.of(9, 7),   // Independência
            MonthDay.of(10, 12), // Nossa Senhora Aparecida
            MonthDay.of(11, 2),  // Finados
            MonthDay.of(11, 15), // Proclamação da República
            MonthDay.of(12, 25), // Natal
            MonthDay.of(12, 31)  // Sem expediente bancário ao público
    );

    // Dia Nacional de Zumbi e da Consciência Negra é feriado nacional desde 2024 (Lei 14.759/2023)
    private static final MonthDay CONSCIENCIA_NEGRA = MonthDay.of(11, 20);
    private static final int PRIMEIRO_ANO_CONSCIENCIA_NEGRA = 2024;

    private CalendarioBancario() {
    }

    static boolean diaUtil(LocalDate data) {
        if (data.getDayOfWeek() == DayOfWeek.SATURDAY || data.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return false;
        }
        var diaDoAno = MonthDay.from(data);
        if (FERIADOS_FIXOS.contains(diaDoAno)) {
            return false;
        }
        if (diaDoAno.equals(CONSCIENCIA_NEGRA) && data.getYear() >= PRIMEIRO_ANO_CONSCIENCIA_NEGRA) {
            return false;
        }
        var pascoa = pascoa(data.getYear());
        return !data.equals(pascoa.minusDays(48))   // Segunda de Carnaval
                && !data.equals(pascoa.minusDays(47)) // Terça de Carnaval
                && !data.equals(pascoa.minusDays(2))  // Sexta-feira Santa
                && !data.equals(pascoa.plusDays(60)); // Corpus Christi
    }

    static LocalDate ultimoDiaUtil(YearMonth mes) {
        var dia = mes.atEndOfMonth();
        while (!diaUtil(dia)) {
            dia = dia.minusDays(1);
        }
        return dia;
    }

    // Domingo de Páscoa (algoritmo de Meeus/Jones/Butcher, calendário gregoriano)
    static LocalDate pascoa(int ano) {
        int a = ano % 19;
        int b = ano / 100;
        int c = ano % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(ano, mes, dia);
    }
}
