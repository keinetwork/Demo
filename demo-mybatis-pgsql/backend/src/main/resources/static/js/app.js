// Vue 2 앱. 빌드 도구 없이 CDN Vue를 그대로 쓰는 정적 SPA.
// 백엔드는 같은 origin(Spring Boot)에서 /api/** 로 서빙되므로 fetch에 상대 경로만 쓰면 된다.

async function request(url, options) {
    const res = await fetch(url, options);
    if (res.status === 204) {
        return null;
    }
    const body = await res.json().catch(() => null);
    if (!res.ok) {
        const message = (body && (body.message || Object.values(body)[0])) || (res.status + ' 오류');
        throw new Error(message);
    }
    return body;
}

function jsonOptions(method, payload) {
    return {
        method,
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(payload),
    };
}

new Vue({
    el: '#app',
    data: {
        tab: 'users',
        tabs: [
            {id: 'users', label: '사용자'},
            {id: 'profiles', label: '프로필'},
            {id: 'orders', label: '주문'},
            {id: 'contacts', label: '비상연락처'},
        ],
        errorMessage: '',

        users: [],
        userForm: {id: null, name: '', email: ''},

        profiles: [],
        profileForm: {userId: null, phoneNumber: '', birthDate: '', addressLine1: '', marketingOptIn: false},
        profileEditing: false,

        orders: [],
        orderForm: {userId: null, customerName: '', customerEmail: '', customerPhone: '', shippingAddress: '', totalAmount: null},
        withContactResult: null,
        shippingForm: null,

        contacts: [],
        contactSearchUserId: null,
        contactForm: {userId: null, fullName: '', phoneNumber: '', relation: '', primary: false},
    },
    created() {
        this.loadUsers();
    },
    methods: {
        async runOrFail(fn) {
            this.errorMessage = '';
            try {
                await fn();
            } catch (e) {
                this.errorMessage = e.message;
            }
        },

        // 사용자
        loadUsers() {
            return this.runOrFail(async () => {
                this.users = await request('/api/users');
            });
        },
        submitUser() {
            return this.runOrFail(async () => {
                if (this.userForm.id) {
                    await request('/api/users/' + this.userForm.id, jsonOptions('PUT', this.userForm));
                } else {
                    await request('/api/users', jsonOptions('POST', this.userForm));
                }
                this.resetUserForm();
                await this.loadUsers();
            });
        },
        editUser(u) {
            this.userForm = {id: u.id, name: u.name, email: u.email};
        },
        resetUserForm() {
            this.userForm = {id: null, name: '', email: ''};
        },
        deleteUser(id) {
            return this.runOrFail(async () => {
                await request('/api/users/' + id, {method: 'DELETE'});
                await this.loadUsers();
            });
        },

        // 프로필
        loadProfiles() {
            return this.runOrFail(async () => {
                this.profiles = await request('/api/user-profiles');
            });
        },
        submitProfile() {
            return this.runOrFail(async () => {
                if (this.profileEditing) {
                    await request('/api/user-profiles/user/' + this.profileForm.userId, jsonOptions('PUT', this.profileForm));
                } else {
                    await request('/api/user-profiles', jsonOptions('POST', this.profileForm));
                }
                this.resetProfileForm();
                await this.loadProfiles();
            });
        },
        editProfile(p) {
            this.profileForm = {
                userId: p.userId,
                phoneNumber: p.phoneNumber,
                birthDate: p.birthDate,
                addressLine1: p.addressLine1,
                marketingOptIn: p.marketingOptIn,
            };
            this.profileEditing = true;
        },
        resetProfileForm() {
            this.profileForm = {userId: null, phoneNumber: '', birthDate: '', addressLine1: '', marketingOptIn: false};
            this.profileEditing = false;
        },

        // 주문
        loadOrders() {
            return this.runOrFail(async () => {
                this.orders = await request('/api/orders');
            });
        },
        submitOrder() {
            return this.runOrFail(async () => {
                await request('/api/orders', jsonOptions('POST', this.orderForm));
                this.orderForm = {userId: null, customerName: '', customerEmail: '', customerPhone: '', shippingAddress: '', totalAmount: null};
                await this.loadOrders();
            });
        },
        viewOrderWithContact(id) {
            return this.runOrFail(async () => {
                this.withContactResult = await request('/api/orders/' + id + '/with-contact');
            });
        },
        startShippingEdit(o) {
            this.shippingForm = {id: o.id, customerPhone: '', shippingAddress: ''};
        },
        submitShippingUpdate() {
            return this.runOrFail(async () => {
                const id = this.shippingForm.id;
                await request('/api/orders/' + id + '/shipping', jsonOptions('PATCH', this.shippingForm));
                this.shippingForm = null;
                await this.loadOrders();
            });
        },

        // 비상연락처
        loadContacts() {
            return this.runOrFail(async () => {
                if (!this.contactSearchUserId) {
                    throw new Error('조회할 userId를 입력하세요.');
                }
                this.contacts = await request('/api/emergency-contacts/user/' + this.contactSearchUserId);
            });
        },
        submitContact() {
            return this.runOrFail(async () => {
                await request('/api/emergency-contacts', jsonOptions('POST', this.contactForm));
                this.contactSearchUserId = this.contactForm.userId;
                this.contactForm = {userId: null, fullName: '', phoneNumber: '', relation: '', primary: false};
                await this.loadContacts();
            });
        },
    },
});
