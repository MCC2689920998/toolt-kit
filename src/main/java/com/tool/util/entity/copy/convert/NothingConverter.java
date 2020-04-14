package com.tool.util.entity.copy.convert;

import com.tool.util.entity.copy.model.Nothing;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.core.convert.support.DefaultConversionService;

import java.util.Collections;
import java.util.Date;
import java.util.Set;

public class NothingConverter implements ConditionalGenericConverter {

    private DefaultConversionService conversionService = new DefaultConversionService();

    public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
        return sourceType.getType() == Nothing.class && sourceType.getType() != targetType.getType();
    }

    public Set<ConvertiblePair> getConvertibleTypes() {
        return Collections.singleton(new ConvertiblePair(Nothing.class, Object.class));
    }

    public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
        if (targetType.getType() == Date.class) return null;

        try {
            return conversionService.convert("", targetType);
        } catch (ConversionFailedException ex) {
            return conversionService.convert("0", targetType);
        }
    }
}