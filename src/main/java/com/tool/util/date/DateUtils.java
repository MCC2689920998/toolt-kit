package com.tool.util.date;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @Description
 * @Author MCC
 * @Date 2020/4/14 11:14
 * @Version 1.0
 **/
public class DateUtils {

    public static final String yyyy_MM_dd= "yyyy-MM-dd";

    public static final String yyyyMMddHHmmss = "yyyyMMddHHmmss";
    public static final String yyyy_MM_dd_HH_mm_ss = "yyyy-MM-dd HH:mm:ss";

    public static String getStrDateByDateFormat(Date date, String dateFormat) {
        DateFormat df = new SimpleDateFormat(dateFormat);
        String strDate = df.format(date);
        return strDate;
    }

    /**
     * 计算 srcDate 到 tarDate 相差多少天
     *
     * @return 天数
     */
    public static long differToDate(Date srcDate, Date tarDate)  {
        long fd = srcDate.getTime();
        long td = tarDate.getTime();
        return (td - fd) / (24L * 60L * 60L * 1000L);
    }

    /**
     * 计算 srcDate 到 tarDate 相差多少天
     * @return 天数
     */
    public static long differToStrDate(String srcDate, String tarDate, String dateFormat) throws Exception {
        DateFormat df = new SimpleDateFormat(dateFormat);
        Date src = df.parse(srcDate);
        Date tar = df.parse(tarDate);
        return differToDate(src, tar);
    }


}
