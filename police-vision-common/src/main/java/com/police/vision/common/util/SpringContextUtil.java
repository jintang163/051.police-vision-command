package com.police.vision.common.util;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class SpringContextUtil implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        applicationContext = context;
    }

    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public static <T> T getBean(Class<T> clazz) {
        if (applicationContext == null) {
            return null;
        }
        try {
            return applicationContext.getBean(clazz);
        } catch (BeansException e) {
            return null;
        }
    }

    public static <T> T getBean(String name, Class<T> clazz) {
        if (applicationContext == null) {
            return null;
        }
        try {
            return applicationContext.getBean(name, clazz);
        } catch (BeansException e) {
            return null;
        }
    }

    public static Object getBean(String name) {
        if (applicationContext == null) {
            return null;
        }
        try {
            return applicationContext.getBean(name);
        } catch (BeansException e) {
            return null;
        }
    }

    public static String getProperty(String key) {
        if (applicationContext == null) {
            return null;
        }
        return applicationContext.getEnvironment().getProperty(key);
    }

    public static String getProperty(String key, String defaultValue) {
        if (applicationContext == null) {
            return defaultValue;
        }
        return applicationContext.getEnvironment().getProperty(key, defaultValue);
    }

    public static <T> T getProperty(String key, Class<T> targetType) {
        if (applicationContext == null) {
            return null;
        }
        return applicationContext.getEnvironment().getProperty(key, targetType);
    }
}
