import request from '../utils/request'

/** 单据能力中心：动态字段定义 / 双通道模板识别 / 导入导出 */
export const docApi = {
  // 字段定义
  fields: (docType) => request.get('/doc-fields', { params: { docType } }),
  saveFields: (data) => request.post('/doc-fields', data),
  deleteField: (id) => request.delete(`/doc-fields/${id}`),
  // 双通道识别（通道一传统=默认 / 通道二AI）
  recognizeExcel: (formData) => request.post('/doc-fields/recognize/excel', formData),
  recognizeAi: (formData) => request.post('/doc-fields/recognize/ai', formData),
  // 采购单：导入 / 导出 / 模板
  importPurchases: (formData) => request.post('/purchases/import', formData),
  exportPurchases: (params) => request.download('/purchases/export', params),
  importTemplate: () => request.download('/purchases/import-template'),
}
