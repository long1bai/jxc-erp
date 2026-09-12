import request from '../utils/request'

/** 项目模块：项目（工地）管理 + 项目材料成本台账 */
export const projectApi = {
  // 项目
  projects: (params) => request.get('/projects', { params }),
  projectOptions: (params) => request.get('/projects/options', { params }),
  createProject: (data) => request.post('/projects', data),
  updateProject: (id, data) => request.put(`/projects/${id}`, data),
  deleteProject: (id) => request.delete(`/projects/${id}`),
  // 项目材料成本台账
  projectCost: (id) => request.get(`/projects/${id}/cost`),
}
