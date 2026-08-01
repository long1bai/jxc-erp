import request from '../utils/request'

/** 库存与生产模块：库存查询 / 盘点 / 成品入库 / 生产退料 / 采购退货 */
export const inventoryApi = {
  // 库存查询
  inventory: (params) => request.get('/stock/inventory', { params }),
  movements: (params) => request.get('/stock/movements', { params }),

  // 库存盘点
  stockTakes: (params) => request.get('/stock-takes', { params }),
  stockTakeDetail: (id) => request.get(`/stock-takes/${id}`),
  createStockTake: (data) => request.post('/stock-takes', data),
  updateStockTake: (id, data) => request.put(`/stock-takes/${id}`, data),
  confirmStockTake: (id) => request.post(`/stock-takes/${id}/confirm`),
  deleteStockTake: (id) => request.delete(`/stock-takes/${id}`),

  // 成品入库
  productionIns: (params) => request.get('/production-ins', { params }),
  createProductionIn: (data) => request.post('/production-ins', data),
  deleteProductionIn: (id) => request.delete(`/production-ins/${id}`),

  // 生产退料
  productionReturns: (params) => request.get('/production-returns', { params }),
  createProductionReturn: (data) => request.post('/production-returns', data),
  deleteProductionReturn: (id) => request.delete(`/production-returns/${id}`),

  // 退货（采购/销售）
  purchaseReturns: (params) => request.get('/purchase-returns', { params }),
  createPurchaseReturn: (data) => request.post('/purchase-returns', data),
  deletePurchaseReturn: (id) => request.delete(`/purchase-returns/${id}`),
  salesReturns: (params) => request.get('/sales-returns', { params }),
  createSalesReturn: (data) => request.post('/sales-returns', data),
  deleteSalesReturn: (id) => request.delete(`/sales-returns/${id}`),
}
