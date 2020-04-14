package com.tool.util.entity.copy;


import com.tool.util.entity.copy.convert.ExtensibleEntityConverter;
import com.tool.util.entity.copy.convert.GenericConverter;
import com.tool.util.entity.copy.convert.NothingConverter;
import org.springframework.core.convert.support.DefaultConversionService;

public class ConversionUtils {
    private static final DefaultConversionService conversionService;
    static {
        conversionService = new DefaultConversionService();
        conversionService.addConverter(new NothingConverter());
        conversionService.addConverter(new ExtensibleEntityConverter());
        conversionService.addConverter(new GenericConverter());
    }

    public static <T> T convert(Object source, Class<T> type) {
        return conversionService.convert(source, type);
    }
}
