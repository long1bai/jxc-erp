import request from '../utils/request'

/** 财务模块：应收应付 / 收付款 / 结算 / 资金收支 */
export const financeApi = {
  // 应收应付
  receivableSummary: (params) => request.get('/finance/receivable-summary', { params }),
  receivableMonthly: (params) => request.get('/finance/receivable-monthly', { params }),
  payableSummary: (params) => request.get('/finance/payable-summary', { params }),
  payableMonthly: (params) => request.get('/finance/payable-monthly', { params }),
  aging: (params) => request.get('/finance/aging', { params }),
  docList: (params) => request.get('/finance/doc-list', { params }),

  // 收付款单
  vouchers: (type, params) => request.get(`/vouchers/${type}`, { params }),
  createVoucher: (type, data) => request.post(`/vouchers/${type}`, data),
  deleteVoucher: (type, id) => request.delete(`/vouchers/${type}/${id}`),

  // 结算核销
  settlements: (params) => request.get('/finance/settlements', { params }),
  settle: (data) => request.post('/finance/settle', data),
  revokeSettle: (data) => request.post('/finance/settle/revoke', data),

  // 发票
  invoices: (params) => request.get('/finance/invoices', { params }),

  // 资金账户 / 收支 / 转账
  accounts: (params) => request.get('/cash-accounts', { params }),
  createAccount: (data) => request.post('/cash-accounts', data),
  updateAccount: (id, data) => request.put(`/cash-accounts/${id}`, data),
  deleteAccount: (id) => request.delete(`/cash-accounts/${id}`),
  incomeExpenses: (params) => request.get('/income-expenses', { params }),
  createIncomeExpense: (data) => request.post('/income-expenses', data),
  deleteIncomeExpense: (id) => request.delete(`/income-expenses/${id}`),
  transfers: (params) => request.get('/transfers', { params }),
  createTransfer: (data) => request.post('/transfers', data),
  deleteTransfer: (id) => request.delete(`/transfers/${id}`),
  accountBalances: (params) => request.get('/account-reports/balances', { params }),
  accountStats: (params) => request.get('/account-reports/stats', { params }),
  businessReport: (params) => request.get('/account-reports/business', { params }),
}
