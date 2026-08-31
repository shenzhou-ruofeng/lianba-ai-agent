<script setup>
</script>

<template>
  <router-view />
</template>

<style>
* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

html, body {
  font-family: 'PingFang SC', 'Microsoft YaHei', 'Helvetica Neue', Helvetica, Arial, sans-serif;
  font-size: 16px;
  color: #333;
  background-color: #f0f2f5;
  width: 100%;
  height: 100%;
  overflow-x: hidden;
  /* 覆盖 style.css 中对移动端不友好的样式 */
  display: block;
  place-items: unset;
}

#app {
  width: 100%;
  min-height: 100vh;
  /* 覆盖 style.css 中的 max-width 限制 */
  max-width: none;
  text-align: left;
}

a {
  text-decoration: none;
  color: inherit;
}

button {
  cursor: pointer;
  /* 移动端防止按钮被挤压变形 */
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
}

/* 移动端安全区适配 */
@supports (padding-bottom: env(safe-area-inset-bottom)) {
  body {
    padding-bottom: env(safe-area-inset-bottom);
  }
}

/* 响应式字体大小 */
@media (max-width: 768px) {
  html, body {
    font-size: 15px;
  }
}

@media (max-width: 480px) {
  html, body {
    font-size: 14px;
  }
}

/* 滚动条样式 */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

::-webkit-scrollbar-thumb {
  background: #ccc;
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: #aaa;
}

@media (max-width: 480px) {
  ::-webkit-scrollbar {
    width: 3px;
    height: 3px;
  }
}
</style>
