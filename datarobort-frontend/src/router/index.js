import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    component: () => import('../views/Layout.vue'),
    redirect: '/models',
    children: [
      {
        path: 'models',
        name: 'ModelConfig',
        component: () => import('../views/ModelConfig.vue'),
        meta: { title: '模型管理', group: 'platform' },
      },
      {
        path: 'datasources',
        name: 'DatasourceConfig',
        component: () => import('../views/DatasourceConfig.vue'),
        meta: { title: '数据源管理', group: 'platform' },
      },
      {
        path: 'knowledge-bases',
        name: 'KnowledgeBase',
        component: () => import('../views/KnowledgeBase.vue'),
        meta: { title: '知识库管理', group: 'knowledge' },
      },
      {
        path: 'business-knowledge',
        name: 'BusinessKnowledge',
        component: () => import('../views/BusinessKnowledge.vue'),
        meta: { title: '业务知识', group: 'knowledge' },
      },
      {
        path: 'semantic-models',
        name: 'SemanticModel',
        component: () => import('../views/SemanticModel.vue'),
        meta: { title: '语义模型', group: 'knowledge' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

export default router
