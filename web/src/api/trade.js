import request from '../utils/request'

/** 进销存模块：客户订单 / 送货单 / 采购入库 */
export const tradeApi = {
  // 共用基础资料（单据页选客户/物料用）
  suppliers: (params) => request.get('/suppliers', { params }),
  materials: (params) => request.get('/materials', { params }),
  warehouses: (params) => request.get('/warehouses', { params }),
  // 客户订单
  orders: (params) => request.get('/orders', { params }),
  orderDetail: (id) => request.get(`/orders/${id}`),
  createOrder: (data) => request.post('/orders', data),
  deleteOrder: (id) => request.delete(`/orders/${id}`),
  // 送货单
  deliveries: (params) => request.get('/deliveries', { params }),
  deliveryDetail: (id) => request.get(`/deliveries/${id}`),
  createDelivery: (data) => request.post('/deliveries', data),
  deleteDelivery: (id) => request.delete(`/deliveries/${id}`),
  openOrders: (customerId) =>
    request.get('/deliveries/open-orders', { params: { customerId } }),
  // 采购入库
  purchases: (params) => request.get('/purchases', { params }),
  purchaseDetail: (id) => request.get(`/purchases/${id}`),
  createPurchase: (data) => request.post('/purchases', data),
  deletePurchase: (id) => request.delete(`/purchases/${id}`),
  // 独立采购订单（订单→分批入库→执行跟踪）
  poOrders: (params) => request.get('/po-orders', { params }),
  poOrderDetail: (id) => request.get(`/po-orders/${id}`),
  createPoOrder: (data) => request.post('/po-orders', data),
  receivePoOrder: (id, items) => request.post(`/po-orders/${id}/receive`, items),
  deletePoOrder: (id) => request.delete(`/po-orders/${id}`),

  // 销售退货
  salesReturns: (params) => request.get('/sales-returns', { params }),
  salesReturnDetail: (id) => request.get(`/sales-returns/${id}`),
  createSalesReturn: (data) => request.post('/sales-returns', data),
  deleteSalesReturn: (id) => request.delete(`/sales-returns/${id}`),
}
