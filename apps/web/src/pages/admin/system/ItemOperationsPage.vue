<script setup>import{onMounted,ref}from'vue';import{useRoute}from'vue-router';import OperationLogsPage from'./OperationLogsPage.vue';const route=useRoute()</script>
<template><OperationLogsPage :item-id="route.params.itemId"/></template>
