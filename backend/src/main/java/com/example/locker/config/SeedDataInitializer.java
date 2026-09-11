package com.example.locker.config;

import com.example.locker.entity.Building;
import com.example.locker.entity.Locker;
import com.example.locker.entity.Unit;
import com.example.locker.enums.LockerStatus;
import com.example.locker.repository.BuildingRepository;
import com.example.locker.repository.LockerRepository;
import com.example.locker.repository.UnitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 启动时补种基础种子数据（楼栋 / 单元 / 快递柜）。
 *
 * 背景：MySQL 容器的 docker-entrypoint-initdb.d 只在空数据卷首次初始化时执行 schema.sql，
 * 一旦数据卷已初始化（旧版本脚本没有柜体种子、初始化中途失败等），柜体种子永远不会再执行，
 * 表现为「楼栋单元有了但编号柜体没有」，快递柜列表为空、无法发起物业巡检。
 *
 * 本补种器在每次后端启动时（JPA 已就绪）按唯一编码逐条检查，只补缺失的记录，
 * 不修改、不覆盖已有数据，可安全重复执行。
 */
@Component
public class SeedDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataInitializer.class);

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private LockerRepository lockerRepository;

    @Autowired
    private CacheManager cacheManager;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int created = 0;

        created += ensureBuilding("1号楼", "B001", 1);
        created += ensureBuilding("2号楼", "B002", 2);
        created += ensureBuilding("3号楼", "B003", 3);

        created += ensureUnit("B001", "1单元", "U001", 1);
        created += ensureUnit("B001", "2单元", "U002", 2);
        created += ensureUnit("B002", "1单元", "U003", 1);
        created += ensureUnit("B002", "2单元", "U004", 2);
        created += ensureUnit("B003", "1单元", "U005", 1);

        created += ensureLocker("KDG-001", 24, "STANDARD", "B001", "U001", "1层", LocalDate.of(2024, 3, 1));
        created += ensureLocker("KDG-002", 36, "LARGE", "B001", "U002", "1层", LocalDate.of(2024, 3, 5));
        created += ensureLocker("KDG-003", 18, "SMALL", "B002", "U003", "1层", LocalDate.of(2024, 4, 10));
        created += ensureLocker("KDG-004", 30, "MIXED", "B003", "U005", "1层", LocalDate.of(2024, 5, 12));

        if (created > 0) {
            // 楼栋/单元树有 Redis 缓存（1 小时 TTL），补种后立即失效，保证刷新后层级与柜体范围一致
            evictHierarchyCaches();
            log.info("种子数据补种完成，共新增 {} 条记录（楼栋/单元/快递柜）", created);
        } else {
            log.info("种子数据已完整，无需补种");
        }
    }

    private int ensureBuilding(String name, String code, int sortOrder) {
        if (buildingRepository.findByCode(code) != null) {
            return 0;
        }
        Building building = new Building();
        building.setName(name);
        building.setCode(code);
        building.setSortOrder(sortOrder);
        buildingRepository.save(building);
        log.info("补种楼栋: {}（{}）", name, code);
        return 1;
    }

    private int ensureUnit(String buildingCode, String name, String code, int sortOrder) {
        Building building = buildingRepository.findByCode(buildingCode);
        if (building == null) {
            log.warn("补种单元 {} 失败：楼栋 {} 不存在", code, buildingCode);
            return 0;
        }
        if (!unitRepository.findByCode(code).isEmpty()) {
            return 0;
        }
        Unit unit = new Unit();
        unit.setBuildingId(building.getId());
        unit.setName(name);
        unit.setCode(code);
        unit.setSortOrder(sortOrder);
        unitRepository.save(unit);
        log.info("补种单元: {}（{}，归属 {}）", name, code, buildingCode);
        return 1;
    }

    private int ensureLocker(String lockerNo, int compartmentCount, String specType,
                             String buildingCode, String unitCode, String floor, LocalDate installationDate) {
        if (lockerRepository.existsByLockerNo(lockerNo)) {
            return 0;
        }
        Building building = buildingRepository.findByCode(buildingCode);
        List<Unit> units = unitRepository.findByCode(unitCode);
        Unit unit = units.stream()
                .filter(u -> building != null && u.getBuildingId().equals(building.getId()))
                .findFirst()
                .orElse(units.isEmpty() ? null : units.get(0));
        if (building == null || unit == null) {
            log.warn("补种快递柜 {} 失败：楼栋 {} 或单元 {} 不存在", lockerNo, buildingCode, unitCode);
            return 0;
        }
        Locker locker = new Locker();
        locker.setLockerNo(lockerNo);
        locker.setCompartmentCount(compartmentCount);
        locker.setSpecType(specType);
        locker.setBuildingId(building.getId());
        locker.setUnitId(unit.getId());
        locker.setFloor(floor);
        locker.setInstallationDate(installationDate);
        locker.setStatus(LockerStatus.ACTIVE);
        lockerRepository.save(locker);
        log.info("补种快递柜: {}（{} / {}）", lockerNo, buildingCode, unitCode);
        return 1;
    }

    private void evictHierarchyCaches() {
        for (String cacheName : new String[]{"buildingTree", "units"}) {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        }
    }
}
