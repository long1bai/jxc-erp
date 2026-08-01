import request from '../utils/request'

/** 系统目录与基础资料：字典 / 菜单 / 公司配置 / 客户 / 供应商 / 物料 / 仓库 / 用户 / 备份 / 快递 */
export const catalogApi = {
  // 目录接口（后端下发）
  dicts: () => request.get('/dicts'),
  menus: () => request.get('/menus'),
  company: () => request.get('/config/company'),

  // 客户 / 供应商 / 物料 / 仓库（基础资料）
  customers: (params) => request.get('/customers', { params }),
  createCustomer: (data) => request.post('/customers', data),
  updateCustomer: (id, data) => request.put(`/customers/${id}`, data),
  deleteCustomer: (id) => request.delete(`/customers/${id}`),
  suppliers: (params) => request.get('/suppliers', { params }),
  createSupplier: (data) => request.post('/suppliers', data),
  updateSupplier: (id, data) => request.put(`/suppliers/${id}`, data),
  deleteSupplier: (id) => request.delete(`/suppliers/${id}`),
  materials: (params) => request.get('/materials', { params }),
  createMaterial: (data) => request.post('/materials', data),
  updateMaterial: (id, data) => request.put(`/materials/${id}`, data),
  deleteMaterial: (id) => request.delete(`/materials/${id}`),
  warehouses: (params) => request.get('/warehouses', { params }),
  createWarehouse: (data) => request.post('/warehouses', data),
  updateWarehouse: (id, data) => request.put(`/warehouses/${id}`, data),
  deleteWarehouse: (id) => request.delete(`/warehouses/${id}`),

  // BOM
  bom: (params) => request.get('/bom', { params }),
  createBom: (data) => request.post('/bom', data),
  updateBom: (id, data) => request.put(`/bom/${id}`, data),
  deleteBom: (id) => request.delete(`/bom/${id}`),

  // 快递物流
  expressCompanies: (params) => request.get('/express-companies', { params }),
  createExpress: (data) => request.post('/express-companies', data),
  updateExpress: (id, data) => request.put(`/express-companies/${id}`, data),
  deleteExpress: (id) => request.delete(`/express-companies/${id}`),

  // 用户 / 备份
  users: (params) => request.get('/users', { params }),
  createUser: (data) => request.post('/users', data),
  updateUser: (id, data) => request.put(`/users/${id}`, data),
  deleteUser: (id) => request.delete(`/users/${id}`),
  backupList: () => request.get('/backup/list'),
  createBackup: () => request.post('/backup/create'),
  restoreBackup: (data) => request.post('/backup/restore', data),
}
