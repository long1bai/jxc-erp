import request from '../utils/request'

/** 库存模块 */
export const stockApi = {
  inventory: (params) => request.get('/stock/inventory', { params }),
  movements: (params) => request.get('/stock/movements', { params }),
  warehouses: (params) => request.get('/warehouses', { params }),
}
