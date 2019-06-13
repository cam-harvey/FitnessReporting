package com.fitpolo.support.entity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class Step implements Comparable<Step> {
    public String time;
    public String value;

    @Override
    public int compareTo(Step another) {
        Calendar calendar = strDate2Calendar(time, "yyyy-MM-dd HH:mm");
        Calendar anotherCalendar = strDate2Calendar(another.time, "yyyy-MM-dd HH:mm");
        if (calendar.getTime().getTime() > anotherCalendar.getTime().getTime()) {
            return 1;
        }
        if (calendar.getTime().getTime() < anotherCalendar.getTime().getTime()) {
            return -1;
        }
        return 0;
    }

    public Calendar strDate2Calendar(String strDate, String pattern) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        try {
            Date date = sdf.parse(strDate);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            return calendar;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public String toString() {
        return "Step{" +
                "time='" + time + '\'' +
                ", value='" + value + '\'' +
                '}';
    }
}
