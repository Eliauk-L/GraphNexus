package com.graphnexus.application.auth.service;

import com.graphnexus.api.auth.dto.*;
import com.graphnexus.common.ApiResult;
import com.graphnexus.common.PageResult;

import java.util.List;

/**
 * 认证业务接口。
 *
 * @author Jay
 * @date 2026/06/22
 */
public interface AuthService {

    LoginResponse login(LoginRequest request);

    ApiResult<UserVO> createUser(CreateUserRequest request);

    ApiResult<UserVO> updateUser(Long id, UpdateUserRequest request);

    PageResult<UserVO> getUsers(int pageNum, int pageSize);

    List<RoleVO> getRoles();
}