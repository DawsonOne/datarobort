package com.datarobort.web.service;

import com.datarobort.common.crypto.AesCryptoUtil;
import com.datarobort.common.error.ErrorCode;
import com.datarobort.common.exception.BizException;
import com.datarobort.core.datasource.DataSourcePoolManager;
import com.datarobort.core.datasource.MetadataService;
import com.datarobort.core.entity.Datasource;
import com.datarobort.core.mapper.DatasourceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Datasource management: CRUD with password encryption & masking,
 * connection tests, schema refresh.
 */
@Slf4j
@Service
public class DatasourceService {

    private static final String MASK = "******";

    private final DatasourceMapper mapper;
    private final DataSourcePoolManager poolManager;
    private final MetadataService metadataService;

    @Value("${datarobort.crypto-key:datarobort-dev-key-2026}")
    private String cryptoKey;

    public DatasourceService(DatasourceMapper mapper,
                             DataSourcePoolManager poolManager,
                             MetadataService metadataService) {
        this.mapper = mapper;
        this.poolManager = poolManager;
        this.metadataService = metadataService;
    }

    public List<Datasource> list() {
        List<Datasource> list = mapper.selectAll();
        list.forEach(d -> d.setPassword(d.getPassword() == null ? null : MASK));
        return list;
    }

    public Datasource detail(Long id) {
        Datasource d = require(id);
        d.setPassword(d.getPassword() == null ? null : MASK);
        return d;
    }

    @Transactional
    public Datasource create(Datasource d) {
        validate(d);
        checkNameUnique(null, d.getName());
        d.setId(null);
        if (d.getStatus() == null) {
            d.setStatus(1);
        }
        if (d.getPassword() != null && !MASK.equals(d.getPassword())) {
            d.setPassword(AesCryptoUtil.encrypt(d.getPassword(), cryptoKey));
        }
        mapper.insert(d);
        return detail(d.getId());
    }

    @Transactional
    public Datasource update(Long id, Datasource d) {
        Datasource existing = require(id);
        validate(d);
        checkNameUnique(id, d.getName());
        d.setId(id);
        if (d.getPassword() == null || MASK.equals(d.getPassword())) {
            d.setPassword(existing.getPassword());
        } else {
            d.setPassword(AesCryptoUtil.encrypt(d.getPassword(), cryptoKey));
        }
        if (d.getStatus() == null) {
            d.setStatus(existing.getStatus());
        }
        mapper.updateById(d);
        poolManager.evict(id);
        return detail(id);
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        mapper.deleteById(id);
        poolManager.evict(id);
    }

    /** Connectivity test against the live datasource. */
    public Map<String, Object> test(Long id) {
        Datasource d = plainCopy(require(id));
        long start = System.currentTimeMillis();
        boolean ok = poolManager.test(d);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", ok);
        result.put("message", ok ? "连接成功" : "连接失败，请检查连接串与账号");
        result.put("latencyMs", System.currentTimeMillis() - start);
        return result;
    }

    /** Re-crawls table/column metadata into ds_table / ds_column. */
    public Map<String, Object> refreshSchema(Long id) {
        Datasource d = plainCopy(require(id));
        int tables = metadataService.refresh(d);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("tableCount", tables);
        result.put("message", "已抓取 " + tables + " 张表的元数据");
        return result;
    }

    /** Schema tree for the frontend. */
    public Object schemaTree(Long id) {
        require(id);
        return metadataService.schemaTree(id);
    }

    private Datasource plainCopy(Datasource d) {
        Datasource copy = new Datasource();
        copy.setId(d.getId());
        copy.setName(d.getName());
        copy.setType(d.getType());
        copy.setJdbcUrl(d.getJdbcUrl());
        copy.setUsername(d.getUsername());
        copy.setDescription(d.getDescription());
        copy.setStatus(d.getStatus());
        copy.setCreateTime(d.getCreateTime());
        copy.setUpdateTime(d.getUpdateTime());
        String pwd = d.getPassword();
        if (pwd != null && !pwd.isEmpty() && !MASK.equals(pwd)) {
            copy.setPassword(AesCryptoUtil.decrypt(pwd, cryptoKey));
        }
        return copy;
    }

    private Datasource require(Long id) {
        Datasource d = mapper.selectById(id);
        if (d == null) {
            throw new BizException(ErrorCode.DS_NOT_FOUND);
        }
        return d;
    }

    private void validate(Datasource d) {
        if (d == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "参数不能为空");
        }
        if (d.getName() == null || d.getName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "name 不能为空");
        }
        if (d.getType() == null || d.getType().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "type 不能为空");
        }
        String t = d.getType().toLowerCase();
        if (!"mysql".equals(t) && !"postgresql".equals(t) && !"pg".equals(t)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "type 仅支持 mysql 或 postgresql");
        }
        if (d.getJdbcUrl() == null || d.getJdbcUrl().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "jdbcUrl 不能为空");
        }
    }

    private void checkNameUnique(Long id, String name) {
        Datasource other = mapper.selectByName(name);
        if (other != null && !other.getId().equals(id)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "数据源名称已存在: " + name);
        }
    }
}
