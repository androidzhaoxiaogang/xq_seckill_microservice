package net.lovexq.seckill.background.system.service.impl;

import net.lovexq.seckill.background.core.repository.cache.ByteRedisClient;
import net.lovexq.seckill.background.system.model.SystemConfigModel;
import net.lovexq.seckill.background.system.repository.SystemConfigRepository;
import net.lovexq.seckill.background.system.service.ConfigService;
import net.lovexq.seckill.common.utils.CacheKeyGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author LuPindong
 * @time 2017-05-01 09:43
 */
@Service
public class ConfigServiceImpl implements ConfigService {

    @Autowired
    private SystemConfigRepository systemConfigRepository;

    @Autowired
    private ByteRedisClient byteRedisClient;

    @Override
    public void save(SystemConfigModel model) {
        systemConfigRepository.save(model);
    }

    @Override
    public SystemConfigModel getByConfigKey(String key) {
        String cacheKey = CacheKeyGenerator.generate(SystemConfigModel.class, "getByConfigKey", key);

        SystemConfigModel sysConfigModel = byteRedisClient.getByteObj(cacheKey, SystemConfigModel.class);
        if (sysConfigModel != null) {
            return sysConfigModel;
        } else {
            sysConfigModel = systemConfigRepository.findByConfigKey(key);
            byteRedisClient.setByteObj(cacheKey, sysConfigModel);
            return sysConfigModel;
        }
    }
}