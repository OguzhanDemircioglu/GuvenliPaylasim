package com.server.modules.report;

import java.util.List;

public interface ReportService {

    /** Son {@code days} günde eklenen kategori sayısı, gün gün; kayıt olmayan günler 0 döner. */
    List<PeriodValue> categoriesCreatedPerDay(int days);
}
