<template>
  <div class="help-page">
    <el-card shadow="never">
      <template #header>
        <span><el-icon><QuestionFilled /></el-icon> 使用指南</span>
      </template>
      <el-tabs v-model="activeTab" class="help-tabs">
        <!-- ============ Tab 1 总览 ============ -->
        <el-tab-pane label="📋 总览" name="overview">
          <div v-if="activeTab === 'overview'">
            <div class="mb-2">
              <div class="tier-label">📱 客户端</div>
              <div class="mod-grid">
                <div class="mod-box mod-blue"><div class="mod-head">🌐 Web 浏览器</div><div class="mod-body">电脑 / 手机 / 平板<br>自适应布局</div></div>
                <div class="mod-box mod-blue"><div class="mod-head">🤖 AI 接口</div><div class="mod-body">报价助手<br>拍照 OCR 识别</div></div>
              </div>
              <div class="arrow-down">⬇ HTTP ⬇</div>
              <div class="tier-label">🖥 Web 服务层 (Spring Boot)</div>
              <div class="mod-grid">
                <div class="mod-box mod-green"><div class="mod-head">🔐 auth</div><div class="mod-body">登录/注销<br>Token 管理</div></div>
                <div class="mod-box mod-green"><div class="mod-head">📋 orders</div><div class="mod-body">客户订单<br>分批出货追踪</div></div>
                <div class="mod-box mod-green"><div class="mod-head">🚚 deliveries</div><div class="mod-body">送货单/打印<br>BOM 扣库存</div></div>
                <div class="mod-box mod-green"><div class="mod-head">📦 purchases</div><div class="mod-body">进货入库<br>拍照OCR</div></div>
                <div class="mod-box mod-green"><div class="mod-head">👷 work</div><div class="mod-body">报工/计时/统计<br>工序扣料</div></div>
                <div class="mod-box mod-green"><div class="mod-head">📊 reports</div><div class="mod-body">报表/对账<br>月度汇总</div></div>
                <div class="mod-box mod-green"><div class="mod-head">⚙️ base</div><div class="mod-body">物料/客户/供应商<br>BOM 配置</div></div>
                <div class="mod-box mod-green"><div class="mod-head">💬 ai</div><div class="mod-body">AI 报价助手<br>数据库查询</div></div>
              </div>
              <div class="arrow-down">⬇ 调用业务层 ⬇</div>
              <div class="tier-label">⚙️ 业务逻辑层 (Service)</div>
              <div class="mod-grid">
                <div class="mod-box mod-orange"><div class="mod-head">📋 基础资料</div><div class="mod-body">供应商 / 客户<br>物料 / 仓库</div></div>
                <div class="mod-box mod-orange"><div class="mod-head">📄 客户订单</div><div class="mod-body">订单 CRUD<br>分批出货</div></div>
                <div class="mod-box mod-orange"><div class="mod-head">🚚 出货管理</div><div class="mod-body">送货单 CRUD<br>打印次数</div></div>
                <div class="mod-box mod-orange"><div class="mod-head">📦 进货管理</div><div class="mod-body">采购单 CRUD<br>OCR 解析</div></div>
                <div class="mod-box mod-orange"><div class="mod-head">👷 报工管理</div><div class="mod-body">开始/结束/提交<br>统计/趋势</div></div>
                <div class="mod-box mod-orange"><div class="mod-head">📊 报表/对账</div><div class="mod-body">月度进销<br>对账单</div></div>
                <div class="mod-box mod-orange"><div class="mod-head">🧩 BOM 管理</div><div class="mod-body">配方/展开<br>工序物料</div></div>
                <div class="mod-box mod-orange"><div class="mod-head">🔐 用户认证</div><div class="mod-body">用户管理<br>登录鉴权</div></div>
              </div>
              <div class="arrow-down">⬇ SQL ⬇</div>
              <div class="tier-label">🗄️ 数据层 (MySQL 8)</div>
              <div class="db-box">
                <div class="db-table"><b>基础资料</b><br>suppliers<br>customers<br>materials</div>
                <div class="db-table"><b>订单模块</b><br>customer_orders<br>customer_order_items</div>
                <div class="db-table"><b>进货模块</b><br>purchase_orders<br>purchase_items</div>
                <div class="db-table"><b>出货模块</b><br>delivery_notes<br>delivery_items</div>
                <div class="db-table"><b>库存流水</b><br>stock_movements</div>
                <div class="db-table"><b>报工模块</b><br>work_reports<br>processes<br>process_materials</div>
                <div class="db-table"><b>系统</b><br>users</div>
              </div>
            </div>

            <div class="mb-2">
              <div class="flow-sub">进（入库）→ 产（报工）→ 销（出货）</div>
              <div class="diagram-box">
                <MermaidFlow id="flow-biz" :code="BIZ_FLOW" />
              </div>
            </div>

            <div class="info-table">
              <div class="info-row"><span class="k">公司</span><span class="v">示例公司</span></div>
              <div class="info-row"><span class="k">地址</span><span class="v">（公司地址）</span></div>
              <div class="info-row"><span class="k">电话</span><span class="v">（联系电话）</span></div>
              <div class="info-row"><span class="k">系统</span><span class="v">本机 http://localhost:8080　局域网 http://192.168.1.88:8080　外网 http://9087hzlk8738.vicp.fun</span></div>
              <div class="info-row"><span class="k">账号</span><span class="v">admin / admin123</span></div>
            </div>
          </div>
        </el-tab-pane>

        <!-- ============ Tab 2 常用业务 ============ -->
        <el-tab-pane label="📦 常用业务" name="biz">
          <div v-if="activeTab === 'biz'">
            <div class="mb-2">
              <div class="tier-label">👷 报工</div>
              <div class="row-flex">
                <div class="col-main">
                  <div class="diagram-box">
                    <MermaidFlow id="flow-work" :code="WORK_FLOW" />
                  </div>
                </div>
                <div class="col-side">
                  <div class="step"><span class="step-num">1</span><div class="step-text">选工序：裁线/端子/组装</div></div>
                  <div class="step"><span class="step-num">2</span><div class="step-text">点开始，自动计时</div></div>
                  <div class="step"><span class="step-num">3</span><div class="step-text">干完填生产数量</div></div>
                  <div class="step"><span class="step-num">4</span><div class="step-text">点结束，算工时+扣料</div></div>
                </div>
              </div>
              <div class="tip">💡 休息 12:00-13:00 和 17:30-18:00 自动扣减。记录有误时，管理员可去"报工登记 → 报工记录"删除后补录。</div>
            </div>

            <div class="mb-2">
              <div class="tier-label">📸 拍照入库</div>
              <div class="row-flex">
                <div class="col-main">
                  <div class="diagram-box">
                    <MermaidFlow id="flow-photo" :code="PHOTO_FLOW" />
                  </div>
                </div>
                <div class="col-side">
                  <div class="step"><span class="step-num">1</span><div class="step-text">拍供应商送货单</div></div>
                  <div class="step"><span class="step-num">2</span><div class="step-text">自动识别供应商+物料</div></div>
                  <div class="step"><span class="step-num">3</span><div class="step-text">核对改错别字</div></div>
                  <div class="step"><span class="step-num">4</span><div class="step-text">自动入库+加库存</div></div>
                </div>
              </div>
              <div class="tip">💡 拍照放平、光线足。横拍效果更好。识别后手动校正再确认。</div>
            </div>

            <div class="mb-2">
              <div class="tier-label">🚚 送货单</div>
              <div class="row-flex">
                <div class="col-main">
                  <div class="diagram-box">
                    <MermaidFlow id="flow-delivery" :code="DELIVERY_FLOW" />
                  </div>
                </div>
                <div class="col-side">
                  <div class="step"><span class="step-num">1</span><div class="step-text">新建，选按订单或临时</div></div>
                  <div class="step"><span class="step-num">2</span><div class="step-text">填数量，保存</div></div>
                  <div class="step"><span class="step-num">3</span><div class="step-text">打印（横向打印，四联格式）</div></div>
                  <div class="step"><span class="step-num">4</span><div class="step-text">客户签收，自动扣库存</div></div>
                </div>
              </div>
              <div class="warn">⚠️ 按订单出货不能超量。临时出货不受限。超过 5 个需核实。</div>
            </div>

            <div class="mb-2">
              <div class="tier-label">📋 订单 → 对账</div>
              <div class="row-flex">
                <div class="col-main">
                  <div class="diagram-box">
                    <MermaidFlow id="flow-order" :code="ORDER_FLOW" />
                  </div>
                </div>
                <div class="col-side">
                  <div class="step"><span class="step-num">1</span><div class="step-text">客户下单 → <span class="hl">客户订单</span> 新建</div></div>
                  <div class="step"><span class="step-num">2</span><div class="step-text">生产完 → <span class="hl">送货单</span> 按订单出货</div></div>
                  <div class="step"><span class="step-num">3</span><div class="step-text">月底 → <span class="hl">报表中心-销售对账</span> 打印</div></div>
                </div>
              </div>
            </div>

            <div class="mb-2">
              <div class="tier-label">📦 库存变化</div>
              <div class="diagram-box">
                <MermaidFlow id="flow-stock" :code="STOCK_FLOW" />
              </div>
            </div>

            <div class="mb-2">
              <div class="tier-label">📊 库存盘点</div>
              <div class="row-flex">
                <div class="col-main">
                  <div class="diagram-box">
                    <MermaidFlow id="flow-take" :code="TAKE_FLOW" />
                  </div>
                </div>
                <div class="col-side">
                  <div class="step"><span class="step-num">1</span><div class="step-text">侧栏 → 库存盘点 → 新建盘点单</div></div>
                  <div class="step"><span class="step-num">2</span><div class="step-text">选物料（可多选），<span class="hl">账面库存自动带出</span></div></div>
                  <div class="step"><span class="step-num">3</span><div class="step-text">填实盘数量（对照盘点表）</div></div>
                  <div class="step"><span class="step-num">4</span><div class="step-text">保存 → <span class="hl">确认</span> → 差异自动调整库存</div></div>
                </div>
              </div>
              <div class="tip">💡 盘盈/盘亏自动生成库存流水。第一次盘点录入的实盘数就是期初库存。确认后要改 → 删除盘点单（库存自动回退）再重新盘。</div>
            </div>

            <div class="mb-2">
              <div class="tier-label">↩️ 退货：销售退货 / 采购退货</div>
              <div class="row-flex">
                <div class="col-main">
                  <div class="diagram-box">
                    <MermaidFlow id="flow-return" :code="RETURN_FLOW" />
                  </div>
                </div>
                <div class="col-side">
                  <div class="step"><span class="step-num">1</span><div class="step-text">客户退货 → <span class="hl">销售退货</span> 选客户+物料+数量</div></div>
                  <div class="step"><span class="step-num">2</span><div class="step-text">保存后：<b>库存自动回补</b> + 应收自动冲减</div></div>
                  <div class="step"><span class="step-num">3</span><div class="step-text">退给供应商 → <span class="hl">采购退货</span>（库存自动减少）</div></div>
                  <div class="step"><span class="step-num">4</span><div class="step-text">删单 → 库存/应收/应付自动回退</div></div>
                </div>
              </div>
              <div class="tip">💡 销售退货单号 XSTH、采购退货 TH。退货不影响已收/已付款，应收应付汇总自动减退货金额。</div>
            </div>
          </div>
        </el-tab-pane>

        <!-- ============ Tab 3 生产与财务 ============ -->
        <el-tab-pane label="💰 生产与财务" name="finance">
          <div v-if="activeTab === 'finance'">
            <div class="mb-2">
              <div class="tier-label">🏭 生产：成品入库 / 退料</div>
              <div class="row-flex">
                <div class="col-main">
                  <div class="diagram-box">
                    <MermaidFlow id="flow-prod" :code="PROD_FLOW" />
                  </div>
                </div>
                <div class="col-side">
                  <div class="step"><span class="step-num">1</span><div class="step-text">车间做完工 → <span class="hl">生产 → 成品入库</span> 选产品填数量</div></div>
                  <div class="step"><span class="step-num">2</span><div class="step-text">成品进库存（自动生成 in 流水）</div></div>
                  <div class="step"><span class="step-num">3</span><div class="step-text">车间剩料/报工多扣 → <span class="hl">生产退料</span> 退回仓库</div></div>
                  <div class="step"><span class="step-num">4</span><div class="step-text"><span class="hl">生产统计</span> 按产品/物料×月看产出</div></div>
                </div>
              </div>
              <div class="tip">💡 成品入库<b>不重复扣料</b>——材料在报工时已按 BOM 自动扣减。生产领料也不用单独开单，报工即领料。</div>
            </div>

            <div class="mb-2">
              <div class="tier-label">🏦 资金与收支</div>
              <div class="row-flex">
                <div class="col-main">
                  <div class="diagram-box">
                    <MermaidFlow id="flow-fund" :code="FUND_FLOW" />
                  </div>
                </div>
                <div class="col-side">
                  <div class="step"><span class="step-num">1</span><div class="step-text">先建<span class="hl">资金账户</span>：现金/银行卡/微信支付宝（可设期初余额）</div></div>
                  <div class="step"><span class="step-num">2</span><div class="step-text">水电/房租/运费/人工 → <span class="hl">收支转账 → 记支出</span></div></div>
                  <div class="step"><span class="step-num">3</span><div class="step-text">废料收入等 → 记收入</div></div>
                  <div class="step"><span class="step-num">4</span><div class="step-text">账户间转钱 → 转账；<span class="hl">收支报表</span>看余额/分类/月度</div></div>
                </div>
              </div>
              <div class="tip">💡 每个账户余额 = 期初 + 收入 − 支出 + 转入 − 转出，自动计算。删单自动回退。老板看利润 = 销售毛利 − 支出 + 其他收入。</div>
            </div>

            <div class="mb-2">
              <div class="tier-label">📈 报表怎么看</div>
              <div class="row-flex">
                <div class="col-main">
                  <div class="diagram-box">
                    <MermaidFlow id="flow-report" :code="REPORT_FLOW" />
                  </div>
                </div>
                <div class="col-side">
                  <div class="step"><span class="step-num">1</span><div class="step-text"><span class="hl">采购报表</span>：按供应商/商品/仓库 + 月度 + 价格趋势 + 退货统计</div></div>
                  <div class="step"><span class="step-num">2</span><div class="step-text"><span class="hl">销售报表</span>：按客户/商品（含毛利）/仓库 + 月度 + 退货统计</div></div>
                  <div class="step"><span class="step-num">3</span><div class="step-text"><span class="hl">收支报表</span>：账户余额 + 收支统计 + 经营状况月报</div></div>
                  <div class="step"><span class="step-num">4</span><div class="step-text">报表中心：对账单打印/应收应付</div></div>
                </div>
              </div>
              <div class="tip">💡 报表数据都是实时计算的（不是写死的数字），和库存/单据对得上。老板角色能看到全部报表。</div>
            </div>
          </div>
        </el-tab-pane>

        <!-- ============ Tab 4 系统维护 ============ -->
        <el-tab-pane label="🛠 系统维护" name="system">
          <div v-if="activeTab === 'system'">
            <div class="mb-2">
              <div class="tier-label">💾 数据备份与恢复</div>
              <div class="row-flex">
                <div class="col-main">
                  <div class="diagram-box">
                    <MermaidFlow id="flow-backup" :code="BACKUP_FLOW" />
                  </div>
                </div>
                <div class="col-side">
                  <div class="step"><span class="step-num">1</span><div class="step-text">系统 → 数据备份 → <span class="hl">立即备份</span>（全库 SQL）</div></div>
                  <div class="step"><span class="step-num">2</span><div class="step-text">备份文件列表：可<span class="hl">下载</span>到电脑存档</div></div>
                  <div class="step"><span class="step-num">3</span><div class="step-text">要恢复 → 点<span class="hl">恢复</span>（连续两次确认）</div></div>
                  <div class="step"><span class="step-num">4</span><div class="step-text">⚠️ 恢复会用备份时的数据<b>覆盖当前数据</b>，慎用</div></div>
                </div>
              </div>
              <div class="warn">⚠️ 恢复前最好先备份一次当前数据（备份文件会自动按时间命名，互不覆盖）。建议每周备份一次。</div>
            </div>

            <div class="mb-2">
              <div class="tier-label">👤 账号与角色</div>
              <div class="row-flex">
                <div class="col-main">
                  <div class="diagram-box">
                    <MermaidFlow id="flow-role" :code="ROLE_FLOW" />
                  </div>
                </div>
                <div class="col-side">
                  <div class="step"><span class="step-num">1</span><div class="step-text"><span class="hl">管理员</span>：全部功能（单据/报表/财务/基础/系统）</div></div>
                  <div class="step"><span class="step-num">2</span><div class="step-text"><span class="hl">老板</span>：只看报表和库存（含财务看数）</div></div>
                  <div class="step"><span class="step-num">3</span><div class="step-text"><span class="hl">开发</span>：维护类（基础资料/BOM/报工设置/系统）</div></div>
                  <div class="step"><span class="step-num">4</span><div class="step-text"><span class="hl">员工</span>：报工登记/拍照入库/库存查询</div></div>
                </div>
              </div>
              <div class="tip">💡 菜单是后端按角色动态下发的——新建账号在 系统 → 用户管理，选角色即可，菜单自动对应。</div>
            </div>

            <div class="faq">
              <div class="tier-label">❓ 常见问题</div>
              <p><strong>报工忘填数量？</strong><br><span>管理员在"报工登记 → 报工记录"删除该条，再点"补录报工"重新录入。</span></p>
              <p><strong>送货单打错了？</strong><br><span>可直接删除，库存自动加回。已打印的注意收回纸质联。</span></p>
              <p><strong>拍照识别不准？</strong><br><span>放平+光线足+横拍，然后手动改。</span></p>
              <p><strong>数据丢了？</strong><br><span>用"系统-数据备份"定期备份。</span></p>
              <p><strong>菜单没显示新功能？</strong><br><span>浏览器 Ctrl+F5 强刷（手机清缓存或隐私模式）。</span></p>
              <p><strong>提示登录失效？</strong><br><span>服务重启过会清掉登录状态，重新登录即可。</span></p>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { QuestionFilled } from '@element-plus/icons-vue'
import MermaidFlow from '../components/MermaidFlow.vue'

const activeTab = ref('overview')

// ===== Mermaid 流程图定义（改文字即改图，比手写 SVG 好维护） =====
const BIZ_FLOW = `
flowchart LR
    A["🏭 供应商"] -->|"供货"| B["📸 拍照入库 +库存"]
    B --> C["🏪 原料库存"]
    C -->|"BOM扣组件"| D["👷 报工扣料 -库存"]
    D --> E["🚚 送货出库 -成品库存"]
`

const WORK_FLOW = `
flowchart LR
    A["1️⃣ 选工序"] --> B["2️⃣ 点开始"]
    B --> C["3️⃣ 填数量"]
    C --> D["4️⃣ 点结束"]
`

const PHOTO_FLOW = `
flowchart LR
    A["📷 拍照"] --> B["🤖 AI识别"]
    B --> C["✏️ 核对"]
    C --> D["✅ 确认入库"]
`

const DELIVERY_FLOW = `
flowchart LR
    A["🆕 新建送货单"] --> B["📋 按订单出货"]
    A --> C["⚡ 临时出货"]
    B --> D["🖨 打印"]
    C --> D
    D --> E["📦 客户签收"]
`

const ORDER_FLOW = `
flowchart LR
    A["📋 客户订单"] -->|"分批出货"| B["🚚 送货"]
    B --> C["✅ 出完"]
    C --> D["🤝 对账打印"]
`

const STOCK_FLOW = `
flowchart LR
    A["📸 拍照入库"] -->|"+库存"| B["🏪 当前库存"]
    B -->|"送货/报工扣"| C["➖ 出库"]
    C --> D["⚠️ 库存预警"]
`

const TAKE_FLOW = `
flowchart LR
    A["🆕 新建盘点单"] --> B["📦 选物料"]
    B --> C["📋 账面自动带出"]
    C --> D["✏️ 填实盘数"]
    D --> E["✅ 确认调整库存"]
`

const FUND_FLOW = `
flowchart LR
    A["🏦 建资金账户"] --> B["💸 记支出"]
    A --> C["💰 记收入"]
    B --> D["📊 收支报表"]
    C --> D
    A --> E["🔁 账户转账"]
    E --> D
`

const PROD_FLOW = `
flowchart LR
    A["🏭 车间完工"] --> B["📦 成品入库 +库存"]
    B --> C["📊 生产统计"]
    A --> D["↩️ 剩余料退料 +库存"]
`

const RETURN_FLOW = `
flowchart LR
    A["客户退货"] --> B["销售退货"]
    B --> C["📦 库存回补"]
    B --> D["🧾 应收冲减"]
    E["退供应商"] --> F["采购退货 -库存"]
`

const REPORT_FLOW = `
flowchart LR
    A["📈 采购报表"] --> D["👀 老板看数"]
    B["📈 销售报表"] --> D
    C["📈 收支报表"] --> D
`

const BACKUP_FLOW = `
flowchart LR
    A["🆕 立即备份"] --> B["💾 备份文件列表"]
    B --> C["⬇ 下载存档"]
    B --> D["♻ 恢复（双确认）"]
`

const ROLE_FLOW = `
flowchart LR
    A["👨‍💼 管理员"] --> E["🗂 全部功能"]
    B["👔 老板"] --> F["📊 报表看数"]
    C["🔧 开发"] --> G["🛠 维护类"]
    D["👷 员工"] --> H["📝 报工/拍照"]
`
</script>

<style scoped>
.help-page { max-width: 1080px; margin: 0 auto; }
.help-tabs { min-height: 320px; }
.flow-sub { margin: 0 0 8px; color: #909399; font-size: 12px; }
.tier-label { font-size: 13px; color: #303133; font-weight: 600; margin: 12px 0 6px; letter-spacing: 1px; padding-left: 8px; border-left: 3px solid #1890ff; }
.arrow-down { text-align: center; color: #ccc; font-size: 14px; line-height: 1; margin: 2px 0; }
.mod-grid { display: flex; flex-wrap: wrap; gap: 6px; }
.mod-box { flex: 1; min-width: 130px; border: 1px solid #e0e0e0; border-radius: 6px; background: #fff; overflow: hidden; }
.mod-head { padding: 5px 8px; font-size: 12px; font-weight: 600; border-bottom: 1px solid #e0e0e0; color: #fff; }
.mod-body { padding: 4px 8px 6px; font-size: 11px; color: #666; line-height: 1.5; }
.mod-blue .mod-head { background: #1890ff; }
.mod-green .mod-head { background: #52c41a; }
.mod-orange .mod-head { background: #fa8c16; }
.db-box { display: flex; flex-wrap: wrap; gap: 4px; }
.db-table { font-size: 10px; background: #f6f8fa; border: 1px solid #e8e8e8; border-radius: 4px; padding: 3px 6px; color: #888; }
.db-table b { color: #1890ff; }
.diagram-box { background: #fafbfc; border: 1px solid #e8e8e8; border-radius: 6px; padding: 8px; text-align: center; overflow-x: auto; }
.row-flex { display: flex; gap: 14px; flex-wrap: wrap; }
.col-main { flex: 1 1 420px; min-width: 280px; }
.col-side { flex: 0 1 260px; }
.step { display: flex; gap: 8px; margin-bottom: 6px; align-items: flex-start; }
.step-num { width: 20px; height: 20px; background: #1890ff; color: #fff; border-radius: 50%; text-align: center; line-height: 20px; font-size: 11px; font-weight: bold; flex-shrink: 0; margin-top: 1px; }
.step-text { font-size: 13px; color: #333; line-height: 1.5; }
.hl { background: #fff7e6; padding: 1px 5px; border-radius: 3px; font-weight: 500; }
.tip { background: #f6f8fa; border-left: 3px solid #52c41a; padding: 6px 10px; margin-top: 8px; font-size: 12px; color: #555; border-radius: 0 4px 4px 0; }
.warn { background: #fff7e6; border-left: 3px solid #faad14; padding: 6px 10px; margin-top: 8px; font-size: 12px; color: #555; border-radius: 0 4px 4px 0; }
.info-table { font-size: 12px; }
.info-row { display: flex; padding: 4px 6px; }
.info-row .k { color: #909399; width: 60px; flex-shrink: 0; }
.info-row .v { font-weight: 600; color: #303133; }
.faq p { margin: 0 0 8px; font-size: 12px; }
.faq strong { color: #303133; }
.faq span { color: #909399; }
.mb-2 { margin-bottom: 16px; }
</style>
