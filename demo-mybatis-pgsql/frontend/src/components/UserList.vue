<template>
  <div class="user-list">
    <h2>Users</h2>

    <form class="user-form" @submit.prevent="submit">
      <input v-model="form.name" type="text" placeholder="name" />
      <input v-model="form.email" type="email" placeholder="email" />
      <button type="submit">{{ editingId ? 'Update' : 'Create' }}</button>
      <button v-if="editingId" type="button" @click="cancelEdit">Cancel</button>
    </form>

    <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
    <p v-if="loading">Loading...</p>

    <table v-if="users.length">
      <thead>
        <tr>
          <th>id</th>
          <th>name</th>
          <th>email</th>
          <th>createdAt</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="user in users" :key="user.id">
          <td>{{ user.id }}</td>
          <td>{{ user.name }}</td>
          <td>{{ user.email }}</td>
          <td>{{ user.createdAt }}</td>
          <td>
            <button type="button" @click="startEdit(user)">Edit</button>
            <button type="button" @click="remove(user.id)">Delete</button>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else-if="!loading">No users yet.</p>
  </div>
</template>

<script>
import axios from 'axios'

export default {
  name: 'UserList',
  data() {
    return {
      users: [],
      form: { name: '', email: '' },
      editingId: null,
      loading: false,
      errorMessage: ''
    }
  },
  created() {
    this.fetchUsers()
  },
  methods: {
    async fetchUsers() {
      this.loading = true
      this.errorMessage = ''
      try {
        const { data } = await axios.get('/api/users')
        this.users = data
      } catch (e) {
        this.errorMessage = this.extractError(e)
      } finally {
        this.loading = false
      }
    },
    async submit() {
      this.errorMessage = ''
      try {
        if (this.editingId) {
          await axios.put(`/api/users/${this.editingId}`, this.form)
        } else {
          await axios.post('/api/users', this.form)
        }
        this.resetForm()
        await this.fetchUsers()
      } catch (e) {
        this.errorMessage = this.extractError(e)
      }
    },
    startEdit(user) {
      this.editingId = user.id
      this.form = { name: user.name, email: user.email }
    },
    cancelEdit() {
      this.resetForm()
    },
    async remove(id) {
      this.errorMessage = ''
      try {
        await axios.delete(`/api/users/${id}`)
        if (this.editingId === id) {
          this.resetForm()
        }
        await this.fetchUsers()
      } catch (e) {
        this.errorMessage = this.extractError(e)
      }
    },
    resetForm() {
      this.editingId = null
      this.form = { name: '', email: '' }
    },
    extractError(e) {
      const body = e.response && e.response.data
      if (body && typeof body === 'object') {
        const values = Object.values(body)
        if (values.length) {
          return values.join(', ')
        }
      }
      return e.message
    }
  }
}
</script>

<style scoped>
.user-form {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th, td {
  border: 1px solid #ddd;
  padding: 6px 8px;
  text-align: left;
}
.error {
  color: #c0392b;
}
</style>
