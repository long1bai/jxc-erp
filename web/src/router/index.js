import { createRouter, createWebHashHistory } from 'vue-router'
import Login from '../views/Login.vue'
import Layout from '../views/Layout.vue'
import Dashboard from '../views/Dashboard.vue'
import Customers from '../views/Customers.vue'
import Suppliers from '../views/Suppliers.vue'
import Materials from '../views/Materials.vue'
import Purchases from '../views/Purchases.vue'
import PurchaseReturns from '../views/PurchaseReturns.vue'
import Stock from '../views/Stock.vue'
import Orders from '../views/Orders.vue'
import Deliveries from '../views/Deliveries.vue'
import Receivables from '../views/Receivables.vue'
import Payables from '../views/Payables.vue'
import Vouchers from '../views/Vouchers.vue'
import WorkReports from '../views/WorkReports.vue'
import WorkSettings from '../views/WorkSettings.vue'
import DeliveryPrint from '../views/DeliveryPrint.vue'
import Bom from '../views/Bom.vue'
import Warehouse from '../views/Warehouse.vue'
import Reports from '../views/Reports.vue'
import ReconciliationPrint from '../views/ReconciliationPrint.vue'
import AiChat from '../views/AiChat.vue'
import PhotoIn from '../views/PhotoIn.vue'
import WorkStats from '../views/WorkStats.vue'
import PurchaseReports from '../views/PurchaseReports.vue'
import SalesReturns from '../views/SalesReturns.vue'
import SalesReports from '../views/SalesReports.vue'
import StockTakes from '../views/StockTakes.vue'
import Approvals from '../views/Approvals.vue'
import CashAccounts from '../views/CashAccounts.vue'
import Funds from '../views/Funds.vue'
import AccountReports from '../views/AccountReports.vue'
import ProductionIns from '../views/ProductionIns.vue'
import ProductionReturns from '../views/ProductionReturns.vue'
import ProductionStats from '../views/ProductionStats.vue'
import ExpressCompanies from '../views/ExpressCompanies.vue'
import Users from '../views/Users.vue'
import Backup from '../views/Backup.vue'
import Placeholder from '../views/Placeholder.vue'
import Help from '../views/Help.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', component: Login },
    { path: '/deliveries/print/:id', component: DeliveryPrint, meta: { title: '送货单打印', roles: ['admin'] } },
    { path: '/reports/reconciliation-print', component: ReconciliationPrint, meta: { title: '对账单', roles: ['admin', 'boss'] } },
    {
      path: '/',
      component: Layout,
      children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', component: Dashboard, meta: { title: '仪表盘' } },
        { path: 'finance/payables', component: Payables, meta: { title: '应付款', roles: ['admin', 'boss'] } },
        { path: 'finance/receivables', component: Receivables, meta: { title: '应收款', roles: ['admin', 'boss'] } },
        { path: 'finance/vouchers', component: Vouchers, meta: { title: '收付款单', roles: ['admin', 'boss'] } },
        { path: 'work/reports', component: WorkReports, meta: { title: '报工登记' } },
        { path: 'work/settings', component: WorkSettings, meta: { title: '报工基础资料', roles: ['admin', 'dev'] } },
        { path: 'work/stats', component: WorkStats, meta: { title: '报工统计', roles: ['admin', 'dev', 'boss'] } },
        { path: 'purchase-reports', component: PurchaseReports, meta: { title: '采购报表', roles: ['admin', 'dev', 'boss'] } },
        { path: 'sales-returns', component: SalesReturns, meta: { title: '销售退货', roles: ['admin', 'dev'] } },
        { path: 'sales-reports', component: SalesReports, meta: { title: '销售报表', roles: ['admin', 'dev', 'boss'] } },
        { path: 'stock-takes', component: StockTakes, meta: { title: '库存盘点', roles: ['admin', 'dev'] } },
        { path: 'approvals', component: Approvals, meta: { title: '单据审批', roles: ['admin', 'dev'] } },
        { path: 'stock/transfers', component: () => import('../views/StockTransfers.vue'), meta: { title: '库存调拨', roles: ['admin', 'dev'] } },
        { path: 'finance/accounts', component: CashAccounts, meta: { title: '资金账户', roles: ['admin'] } },
        { path: 'finance/funds', component: Funds, meta: { title: '收支转账', roles: ['admin'] } },
        { path: 'finance/account-reports', component: AccountReports, meta: { title: '收支报表', roles: ['admin', 'boss'] } },
        { path: 'production/ins', component: ProductionIns, meta: { title: '成品入库', roles: ['admin', 'dev'] } },
        { path: 'production/returns', component: ProductionReturns, meta: { title: '生产退料', roles: ['admin', 'dev'] } },
        { path: 'production/stats', component: ProductionStats, meta: { title: '生产统计', roles: ['admin', 'dev', 'boss'] } },
        { path: 'base/express', component: ExpressCompanies, meta: { title: '快递物流', roles: ['admin', 'dev'] } },
        { path: 'system/users', component: Users, meta: { title: '用户管理', roles: ['admin', 'dev'] } },
        { path: 'system/backup', component: Backup, meta: { title: '数据备份', roles: ['admin', 'dev'] } },
        { path: 'system/logs', component: () => import('../views/OperationLogs.vue'), meta: { title: '操作日志', roles: ['admin', 'dev'] } },
        { path: 'base/customers', component: Customers, meta: { title: '客户', roles: ['admin', 'dev'] } },
        { path: 'base/suppliers', component: Suppliers, meta: { title: '供应商', roles: ['admin', 'dev'] } },
        { path: 'base/materials', component: Materials, meta: { title: '物料', roles: ['admin', 'dev'] } },
        { path: 'base/warehouses', component: Warehouse, meta: { title: '仓库', roles: ['admin', 'dev'] } },
        { path: 'stock/inventory', component: Stock, meta: { title: '库存查询' } },
        { path: 'purchases', component: Purchases, meta: { title: '采购入库', roles: ['admin'] } },
        { path: 'po-orders', component: () => import('../views/PoOrders.vue'), meta: { title: '采购订单', roles: ['admin'] } },
        { path: 'purchase-returns', component: PurchaseReturns, meta: { title: '采购退货', roles: ['admin'] } },
        { path: 'orders', component: Orders, meta: { title: '客户订单', roles: ['admin'] } },
        { path: 'deliveries', component: Deliveries, meta: { title: '送货单', roles: ['admin'] } },
        { path: 'bom', component: Bom, meta: { title: 'BOM 配方', roles: ['admin', 'dev'] } },
        { path: 'reports', component: Reports, meta: { title: '报表中心', roles: ['admin', 'boss'] } },
        { path: 'ai/chat', component: AiChat, meta: { title: 'AI 报价', roles: ['admin', 'boss', 'dev'] } },
        { path: 'ai/photo', component: PhotoIn, meta: { title: '拍照入库' } },
        { path: 'help', component: Help, meta: { title: '使用指南' } },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  if (to.path !== '/login' && !token) {
    return '/login'
  }
  if (to.path === '/login' && token) {
    return '/'
  }
  // 角色守卫：无权限的角色访问 → 仪表盘
  if (to.meta.roles) {
    let user = {}
    try {
      user = JSON.parse(localStorage.getItem('user') || '{}')
    } catch { /* ignore */ }
    if (!to.meta.roles.includes(user.role)) {
      return '/dashboard'
    }
  }
})

export default router
