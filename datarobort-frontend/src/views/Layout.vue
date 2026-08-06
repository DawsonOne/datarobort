<template>
  <el-container style="height: 100vh">
    <!-- Sidebar -->
    <el-aside width="220px" style="background: #1e293b">
      <div class="logo">
        <span class="logo-text">DataRobort</span>
        <span class="logo-sub">P2 知识增强</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        background-color="#1e293b"
        text-color="#94a3b8"
        active-text-color="#ffffff"
        router
        style="border-right: none"
      >
        <el-menu-item index="/models">
          <el-icon><Cpu /></el-icon>
          <span>模型管理</span>
        </el-menu-item>
        <el-menu-item index="/datasources">
          <el-icon><Coin /></el-icon>
          <span>数据源管理</span>
        </el-menu-item>
        <el-divider style="margin:8px 0;border-color:#334155" />
        <el-menu-item index="/knowledge-bases">
          <el-icon><DocumentCopy /></el-icon>
          <span>知识库管理</span>
        </el-menu-item>
        <el-menu-item index="/business-knowledge">
          <el-icon><CollectionTag /></el-icon>
          <span>业务知识</span>
        </el-menu-item>
        <el-menu-item index="/semantic-models">
          <el-icon><Connection /></el-icon>
          <span>语义模型</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- Main content -->
    <el-container>
      <el-header style="background: #fff; border-bottom: 1px solid #e6e9f2; display: flex; align-items: center; justify-content: space-between">
        <span style="font-size: 16px; font-weight: 700; color: #1e293b">
          {{ $route.meta?.title || 'DataRobort' }}
        </span>
        <el-tag type="success" size="small">里程碑 M1</el-tag>
      </el-header>
      <el-main style="background: #f5f7fb; overflow-y: auto">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const activeMenu = computed(() => route.path)
</script>

<style scoped>
.logo {
  padding: 20px 16px 8px;
  display: flex;
  flex-direction: column;
}
.logo-text {
  font-size: 20px;
  font-weight: 800;
  color: #fff;
  letter-spacing: 0.02em;
}
.logo-sub {
  font-size: 11px;
  color: #64748b;
  margin-top: 2px;
}
</style>
