import request from '../utils/request'

/** 报表模块：对账 / 利润 / 采购统计 / 销售统计 / 生产统计 */
export const reportsApi = {
  // 报表中心
  reconciliation: (params) => request.get('/reports/reconciliation', { params }),
  profit: (params) => request.get('/reports/profit', { params }),

  // 采购统计
  purchaseBySupplier: (params) => request.get('/purchase-stats/by-supplier', { params }),
  purchaseByMaterial: (params) => request.get('/purchase-stats/by-material', { params }),
  purchaseByWarehouse: (params) => request.get('/purchase-stats/by-warehouse', { params }),
  purchaseDetail: (params) => request.get('/purchase-stats/detail', { params }),
  purchaseMonthly: (params) => request.get('/purchase-stats/monthly', { params }),
  purchaseMaterialMonthly: (params) => request.get('/purchase-stats/material-monthly', { params }),
  purchaseSupplierMonthly: (params) => request.get('/purchase-stats/supplier-monthly', { params }),
  purchasePriceTrend: (params) => request.get('/purchase-stats/price-trend', { params }),
  purchaseReturnsBySupplier: (params) => request.get('/purchase-stats/returns-by-supplier', { params }),
  purchaseReturnsByMaterial: (params) => request.get('/purchase-stats/returns-by-material', { params }),

  // 销售统计
  salesByCustomer: (params) => request.get('/sales-stats/by-customer', { params }),
  salesByMaterial: (params) => request.get('/sales-stats/by-material', { params }),
  salesByWarehouse: (params) => request.get('/sales-stats/by-warehouse', { params }),
  salesGrossByCustomer: (params) => request.get('/sales-stats/gross-by-customer', { params }),
  salesDetail: (params) => request.get('/sales-stats/detail', { params }),
  salesMonthly: (params) => request.get('/sales-stats/monthly', { params }),
  salesCustomerMonthly: (params) => request.get('/sales-stats/customer-monthly', { params }),
  salesMaterialMonthly: (params) => request.get('/sales-stats/material-monthly', { params }),
  salesReturnsByCustomer: (params) => request.get('/sales-stats/returns-by-customer', { params }),
  salesReturnsByMaterial: (params) => request.get('/sales-stats/returns-by-material', { params }),

  // 生产统计
  productionIns: (params) => request.get('/production-stats/ins', { params }),
  productionReturns: (params) => request.get('/production-stats/returns', { params }),
}
