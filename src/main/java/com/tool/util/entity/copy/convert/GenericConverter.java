package com.tool.util.entity.copy.convert;

import org.modelmapper.ModelMapper;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;

import java.util.Set;

public class GenericConverter implements ConditionalGenericConverter {
    private static final ModelMapper mapper = new ModelMapper();

    public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
        if (sourceType.getType() == targetType.getType()) return false;
        return true;
    }

    public Set<ConvertiblePair> getConvertibleTypes() {
        return null;
    }

    public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
        return mapper.map(source, targetType.getType());
    }
}
