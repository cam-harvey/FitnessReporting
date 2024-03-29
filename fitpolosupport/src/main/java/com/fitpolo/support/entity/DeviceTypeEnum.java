package com.fitpolo.support.entity;

/**
 * @Date 2018/4/12
 * @Author wenzheng.liu
 * @Description 版本
 * @ClassPath com.fitpolo.support.entity.DeviceTypeEnum
 */
public enum DeviceTypeEnum {
    H703("03"),
    H705("05"),
    H706("06"),
    ;

    private String lastCode;

    DeviceTypeEnum(String lastCode) {
        this.lastCode = lastCode;
    }

    public String getLastCode() {
        return lastCode;
    }

    public void setLastCode(String lastCode) {
        this.lastCode = lastCode;
    }

    public static DeviceTypeEnum fromOrdinal(int ordinal) {
        for (DeviceTypeEnum deviceTypeEnum : DeviceTypeEnum.values()) {
            if (deviceTypeEnum.ordinal() == ordinal) {
                return deviceTypeEnum;
            }
        }
        return H703;
    }

    public static DeviceTypeEnum fromLastCode(String lastCode) {
        for (DeviceTypeEnum deviceTypeEnum : DeviceTypeEnum.values()) {
            if (deviceTypeEnum.getLastCode().equals(lastCode)) {
                return deviceTypeEnum;
            }
        }
        return H703;
    }
}
