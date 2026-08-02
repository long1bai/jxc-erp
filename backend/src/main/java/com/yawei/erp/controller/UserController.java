package com.yawei.erp.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import com.yawei.erp.common.ApiResponse;
import com.yawei.erp.security.PasswordUtils;
import com.yawei.erp.entity.User;
import com.yawei.erp.mapper.UserMapper;
import com.yawei.erp.mapper.WorkMapper;
import com.yawei.erp.service.SysConfigService;

/** 用户管理（列表/新增/改密/角色/禁用） */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserMapper mapper;
    private final WorkMapper workMapper;

        private final SysConfigService cfg;

public UserController(UserMapper mapper, WorkMapper workMapper, SysConfigService cfg) {
        this.mapper = mapper;
        this.cfg = cfg;
        this.workMapper = workMapper;
    }

    /** 按姓名自动关联报工员工（同名即关联；无同名返回 null） */
    private Long autoLinkEmployee(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        var rows = workMapper.employeeByName(name.trim());
        if (rows.isEmpty()) {
            return null;
        }
        Object id = rows.get(0).get("id");
        return id == null ? null : ((Number) id).longValue();
    }

    /** 员工账号：名单无同名时自动创建报工员工并关联（姓名必填；工号 W 流水） */
    private Long autoCreateEmployee(User u) {
        if (u.getDisplayName() == null || u.getDisplayName().isBlank()) {
            return null;
        }
        Long empId = workMapper.nextId();
        Long max = workMapper.maxEmployeeNo();
        String empNo = cfg.get("employee_no_prefix") + String.format("%03d", (max == null ? 0 : max) + 1);
        workMapper.insertEmployeeAuto(empId, empNo, u.getDisplayName());
        return empId;
    }

    @GetMapping
    public Map<String, Object> list() {
        return ApiResponse.ok(Map.of("items", mapper.selectList(new LambdaQueryWrapper<User>()
                .orderByAsc(User::getId))));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody UserReq req) {
        if (req.username() == null || req.username().isBlank()) {
            return ApiResponse.fail("用户名不能为空");
        }
        if (req.password() == null || req.password().length() < 4) {
            return ApiResponse.fail("密码至少 4 位");
        }
        if (mapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, req.username().trim())) > 0) {
            return ApiResponse.fail("用户名已存在");
        }
        User u = new User();
        u.setUsername(req.username().trim());
        u.setPasswordHash(PasswordUtils.encode(req.password()));
        u.setDisplayName(req.displayName() == null ? req.username().trim() : req.displayName());
        u.setRole(req.role() == null ? "user" : req.role());
        // 关联报工员工：显式传了就用；否则按姓名自动匹配；员工角色无同名则自动创建报工员工
        Long linked = (req.workEmployeeId() != null && req.workEmployeeId() > 0)
                ? req.workEmployeeId()
                : autoLinkEmployee(u.getDisplayName());
        if (linked == null && "employee".equals(u.getRole())) {
            linked = autoCreateEmployee(u);
        }
        u.setWorkEmployeeId(linked);
        u.setActive(true);
        mapper.insert(u);
        return ApiResponse.ok(Map.of("id", u.getId()));
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody UserUpdateReq req) {
        User u = mapper.selectById(id);
        if (u == null) {
            return ApiResponse.fail("用户不存在");
        }
        String oldName = u.getDisplayName();
        if (req.displayName() != null) {
            u.setDisplayName(req.displayName());
        }
        if (req.role() != null && !req.role().isBlank()) {
            u.setRole(req.role());
        }
        // 关联报工员工：0 或 null=清除；>0=手动指定；改名时同步员工名（同一记录）或按新名匹配/创建
        if (req.workEmployeeId() != null) {
            u.setWorkEmployeeId(req.workEmployeeId() > 0 ? req.workEmployeeId() : null);
        } else if (req.displayName() != null && !req.displayName().isBlank()
                && !req.displayName().equals(oldName)) {
            Long linked = u.getWorkEmployeeId();
            if (linked != null) {
                // 已有员工关联：直接改名（保持报工记录/工资归属不变）
                workMapper.employeeRename(linked, req.displayName().trim());
            } else {
                linked = autoLinkEmployee(req.displayName());
                if (linked == null && "employee".equals(u.getRole())) {
                    linked = autoCreateEmployee(u);
                }
            }
            u.setWorkEmployeeId(linked);
        }
        if (req.active() != null) {
            u.setActive(req.active());
        }
        if (req.password() != null && !req.password().isBlank()) {
            if (req.password().length() < 4) {
                return ApiResponse.fail("密码至少 4 位");
            }
            u.setPasswordHash(PasswordUtils.encode(req.password()));
        }
        mapper.updateById(u);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        User u = mapper.selectById(id);
        if (u == null) {
            return ApiResponse.fail("用户不存在");
        }
        if ("admin".equals(u.getUsername())) {
            return ApiResponse.fail("系统管理员账号不能删除");
        }
        // 合并后员工与账号同源：有关联员工且员工有报工记录的不能删（可禁用账号）
        Long empId = u.getWorkEmployeeId();
        if (empId != null) {
            Long cnt = workMapper.countReportsByEmployee(empId);
            if (cnt != null && cnt > 0) {
                return ApiResponse.fail("该账号关联的员工已有报工记录，不能删除；可改为禁用");
            }
        }
        // 逻辑删除 + 用户名加后缀（软删后仍可重录同用户名）；关联员工同步逻辑删除
        mapper.softDeleteWithUsernameSuffix(id);
        // 同步删除报工员工（无报工记录才能走到这），避免打卡页残留孤儿员工
        if (empId != null) {
            workMapper.employeeDelete(empId);
        }
        return ApiResponse.ok();
    }

    public record UserReq(String username, String password, String displayName, String role, Long workEmployeeId) {}
    public record UserUpdateReq(String displayName, String role, Boolean active, String password, Long workEmployeeId) {}
}
