import axios from 'axios'

const BASE = '/api/v1/auth'

export interface LoginParams { username: string; password: string }
export interface LoginResult {
  accessToken: string; refreshToken: string; expiresIn: number
  userInfo: { userId: number; username: string; realName: string; status: string; roles: string[] }
}
export interface UserVO {
  id: number; username: string; realName: string; roles: string[]; status: string; createTime: string
}
export interface RoleVO { code: string; name: string }

export const authApi = {
  login(data: LoginParams) { return axios.post<ApiResult<LoginResult>>(`${BASE}/login`, data) },
  refresh(refreshToken: string) { return axios.post<ApiResult<LoginResult>>(`${BASE}/refresh`, { refreshToken }) },
  logout(refreshToken: string) { return axios.post(`${BASE}/logout`, { refreshToken }) },
  getUsers(pageNum = 1, pageSize = 10) { return axios.get(`${BASE}/users`, { params: { pageNum, pageSize } }) },
  createUser(data: any) { return axios.post(`${BASE}/users`, data) },
  updateUser(id: number, data: any) { return axios.put(`${BASE}/users/${id}`, data) },
  getRoles() { return axios.get<ApiResult<RoleVO[]>>(`${BASE}/roles`) },
}

interface ApiResult<T> { code: number; message: string; data: T; traceId: string; timestamp: number }
