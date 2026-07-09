package com.example.locker.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Service
public class SpecTemplateService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String REDIS_KEY = "locker:spec_template";

    private static final Map<String, String> DEFAULT_SPEC_TEMPLATE = new HashMap<>();
    static {
        DEFAULT_SPEC_TEMPLATE.put("STANDARD", "标准型");
        DEFAULT_SPEC_TEMPLATE.put("LARGE", "大型");
        DEFAULT_SPEC_TEMPLATE.put("SMALL", "小型");
        DEFAULT_SPEC_TEMPLATE.put("MIXED", "混合型");
    }

    @PostConstruct
    public void init() {
        String cached = stringRedisTemplate.opsForValue().get(REDIS_KEY);
        if (cached == null) {
            saveSpecTemplate(DEFAULT_SPEC_TEMPLATE);
        }
    }

    public Map<String, String> getSpecTemplate() {
        String cached = stringRedisTemplate.opsForValue().get(REDIS_KEY);
        if (cached != null) {
            return JSON.parseObject(cached, new TypeReference<Map<String, String>>() {});
        }
        return DEFAULT_SPEC_TEMPLATE;
    }

    public void saveSpecTemplate(Map<String, String> template) {
        String json = JSON.toJSONString(template);
        stringRedisTemplate.opsForValue().set(REDIS_KEY, json);
    }

    public void updateSpecTemplate(String code, String name) {
        Map<String, String> template = getSpecTemplate();
        template.put(code, name);
        saveSpecTemplate(template);
    }

    public void deleteSpecTemplate(String code) {
        Map<String, String> template = getSpecTemplate();
        template.remove(code);
        saveSpecTemplate(template);
    }

    public String getSpecTypeName(String code) {
        Map<String, String> template = getSpecTemplate();
        return template.getOrDefault(code, code);
    }
}
