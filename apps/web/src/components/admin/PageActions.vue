<script setup>defineProps({ title: String, description: String })</script>
<template><div class="business-heading"><div><h2>{{ title }}</h2><p v-if="description">{{ description }}</p></div><div class="business-actions"><slot /></div></div></template>
