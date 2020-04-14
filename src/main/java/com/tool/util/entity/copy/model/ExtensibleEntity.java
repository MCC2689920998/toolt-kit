package com.tool.util.entity.copy.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.tool.util.entity.copy.ConversionUtils;
import lombok.Data;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

@Data
public class ExtensibleEntity {
    @JsonIgnore
    Map<String, Object> extension = new HashMap<String, Object>();

    @JsonAnyGetter
    public Map<String, Object> get() {
        return extension;
    }

    @JsonAnySetter
    public void set(String key, Object value) {
        if (setPrimitiveProperty(key, value)) return ;
        extension.put(key, value);
    }

    public <T> T get(String nodeCode, Class<T> type){
        Object result = getPrimitiveProperty(nodeCode);
        if (result == null)
            result = getExtendProperty(nodeCode);

        return ConversionUtils.convert(result, type);
    }

    private boolean setPrimitiveProperty(String key, Object value) {
        Field field = ReflectionUtils.findField(this.getClass(), key);
        if (field == null) return false;

        field.setAccessible(true);
        try {
            ReflectionUtils.setField(field, this, value);
        } catch (Exception ex) {
            return false;
        }

        return true;
    }

    private Object getPrimitiveProperty(String nodeCode) {
        try {
            Field field = ReflectionUtils.findField(this.getClass(), nodeCode);
            field.setAccessible(true);

            return ReflectionUtils.getField(field, this);
        } catch (Throwable ex) {
            return null;
        }
    }

    private Object getExtendProperty(String nodeCode) {
        return extension.get(nodeCode);
    }
}