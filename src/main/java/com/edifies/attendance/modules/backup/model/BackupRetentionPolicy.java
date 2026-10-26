package com.edifies.attendance.modules.backup.model;

public class BackupRetentionPolicy {
    private int keepDaily = 7;
    private int keepWeekly = 4;
    private int keepMonthly = 3;

    public BackupRetentionPolicy() {
    }

    public BackupRetentionPolicy(int keepDaily, int keepWeekly, int keepMonthly) {
        this.keepDaily = Math.max(1, keepDaily);
        this.keepWeekly = Math.max(1, keepWeekly);
        this.keepMonthly = Math.max(1, keepMonthly);
    }

    public int getKeepDaily() {
        return keepDaily;
    }

    public void setKeepDaily(int keepDaily) {
        this.keepDaily = Math.max(1, keepDaily);
    }

    public int getKeepWeekly() {
        return keepWeekly;
    }

    public void setKeepWeekly(int keepWeekly) {
        this.keepWeekly = Math.max(1, keepWeekly);
    }

    public int getKeepMonthly() {
        return keepMonthly;
    }

    public void setKeepMonthly(int keepMonthly) {
        this.keepMonthly = Math.max(1, keepMonthly);
    }
}
