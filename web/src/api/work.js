import request from '../utils/request'

/** 报工模块 */
export const workApi = {
  // 基础资料
  groups: (params) => request.get('/work/groups', { params }),
  createGroup: (data) => request.post('/work/groups', data),
  updateGroup: (id, data) => request.put(`/work/groups/${id}`, data),
  deleteGroup: (id) => request.delete(`/work/groups/${id}`),
  employees: (params) => request.get('/work/employees', { params }),
  createEmployee: (data) => request.post('/work/employees', data),
  updateEmployee: (id, data) => request.put(`/work/employees/${id}`, data),
  deleteEmployee: (id) => request.delete(`/work/employees/${id}`),
  processes: (params) => request.get('/work/processes', { params }),
  createProcess: (data) => request.post('/work/processes', data),
  updateProcess: (id, data) => request.put(`/work/processes/${id}`, data),
  deleteProcess: (id) => request.delete(`/work/processes/${id}`),

  // 报工登记
  reports: (params) => request.get('/work/reports', { params }),
  my: (params) => request.get('/work/reports/my', { params }),
  lastProcess: (params) => request.get('/work/reports/last-process', { params }),
  inProgress: (params) => request.get('/work/reports/in-progress', { params }),
  start: (data) => request.post('/work/reports/start', data),
  finish: (id, data) => request.post(`/work/reports/${id}/finish`, data),
  cancel: (id) => request.post(`/work/reports/${id}/cancel`),

  // 统计
  statsData: (params) => request.get('/work/stats-data', { params }),
  statsDaily: (params) => request.get('/work/stats/daily', { params }),
  statsMonthly: (params) => request.get('/work/stats/monthly', { params }),
  statsWages: (params) => request.get('/work/stats/wages', { params }),
  trend: (params) => request.get('/work/trend', { params }),
}
