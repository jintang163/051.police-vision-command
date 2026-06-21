package com.police.vision.common.util;

import cn.hutool.core.util.StrUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AddressUtil {

    private static final Pattern PROVINCE_PATTERN = Pattern.compile("^(北京|天津|上海|重庆|内蒙古|广西|西藏|宁夏|新疆|[河北|山西|辽宁|吉林|黑龙江|江苏|浙江|安徽|福建|江西|山东|河南|湖北|湖南|广东|海南|四川|贵州|云南|陕西|甘肃|青海]+省)");
    private static final Pattern CITY_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5]+?(?:市|自治州|盟|地区))");
    private static final Pattern DISTRICT_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5]+?(?:区|县|旗|市))");

    private AddressUtil() {}

    public static String parseProvince(String address) {
        if (StrUtil.isBlank(address)) {
            return null;
        }
        Matcher matcher = PROVINCE_PATTERN.matcher(address);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public static String parseCity(String address) {
        if (StrUtil.isBlank(address)) {
            return null;
        }
        Matcher matcher = CITY_PATTERN.matcher(address);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public static String parseDistrict(String address) {
        if (StrUtil.isBlank(address)) {
            return null;
        }
        Matcher matcher = DISTRICT_PATTERN.matcher(address);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public static String getGridCode(String address) {
        String province = parseProvince(address);
        String city = parseCity(address);
        String district = parseDistrict(address);

        StringBuilder sb = new StringBuilder();
        if (province != null) {
            sb.append(province.hashCode() % 1000000);
        }
        if (city != null) {
            sb.append("-").append(city.hashCode() % 1000);
        }
        if (district != null) {
            sb.append("-").append(district.hashCode() % 1000);
        }
        return sb.toString();
    }

    public static String maskPhone(String phone) {
        if (StrUtil.isBlank(phone) || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    public static String maskIdCard(String idCard) {
        if (StrUtil.isBlank(idCard) || idCard.length() < 10) {
            return idCard;
        }
        return idCard.substring(0, 6) + "********" + idCard.substring(idCard.length() - 4);
    }
}
