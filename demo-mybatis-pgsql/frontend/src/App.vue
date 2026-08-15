<template>
  <div id="app">
    <h1>demo-mybatis-pgsql</h1>
    <UserList />
  </div>
</template>

<script>
import UserList from './components/UserList.vue'

export default {
  name: 'App',
  components: { UserList }
}
</script>

<style>
#app {
  font-family: Avenir, Helvetica, Arial, sans-serif;
  max-width: 720px;
  margin: 40px auto;
  padding: 0 16px;
  color: #2c3e50;
}
</style>
