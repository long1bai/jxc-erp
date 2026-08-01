import request from '../utils/request'

/** 基础资料模块：客户 / 供应商 / 物料 */
export const baseApi = {
  // 客户
  customers: (params) => request.get('/customers', { params }),
  createCustomer: (data) => request.post('/customers', data),
  updateCustomer: (id, data) => request.put(`/customers/${id}`, data),
  deleteCustomer: (id) => request.delete(`/customers/${id}`),
  // 供应商
  suppliers: (params) => request.get('/suppliers', { params }),
  createSupplier: (data) => request.post('/suppliers', data),
  updateSupplier: (id, data) => request.put(`/suppliers/${id}`, data),
  deleteSupplier: (id) => request.delete(`/suppliers/${id}`),
  // 物料
  materials: (params) => request.get('/materials', { params }),
  createMaterial: (data) => request.post('/materials', data),
  updateMaterial: (id, data) => request.put(`/materials/${id}`, data),
  deleteMaterial: (id) => request.delete(`/materials/${id}`),
}
