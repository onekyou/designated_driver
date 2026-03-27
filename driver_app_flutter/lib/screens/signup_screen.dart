import 'package:flutter/material.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import '../core/theme.dart';

/// 회원가입 화면 (Kotlin SignUpScreen 포팅)
class SignUpScreen extends StatefulWidget {
  const SignUpScreen({super.key});

  @override
  State<SignUpScreen> createState() => _SignUpScreenState();
}

class _SignUpScreenState extends State<SignUpScreen> {
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  final _confirmPasswordController = TextEditingController();
  final _nameController = TextEditingController();
  final _phoneController = TextEditingController();

  bool _obscurePassword = true;
  bool _obscureConfirm = true;
  bool _isLoading = false;
  String? _error;

  // 3단계 드롭다운
  List<_DropItem> _provinces = [];
  List<_DropItem> _cities = [];
  List<_DropItem> _offices = [];
  _DropItem? _selectedProvince;
  _DropItem? _selectedCity;
  _DropItem? _selectedOffice;

  final _firestore = FirebaseFirestore.instance;

  @override
  void initState() {
    super.initState();
    _loadProvinces();
  }

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    _confirmPasswordController.dispose();
    _nameController.dispose();
    _phoneController.dispose();
    super.dispose();
  }

  Future<void> _loadProvinces() async {
    final snap = await _firestore.collection('provinces').orderBy('name').get();
    setState(() {
      _provinces = snap.docs.map((d) => _DropItem(d.id, d.get('name') as String? ?? d.id)).toList();
    });
  }

  Future<void> _loadCities(String provinceId) async {
    setState(() { _cities = []; _offices = []; _selectedCity = null; _selectedOffice = null; });
    final snap = await _firestore.collection('provinces').doc(provinceId).collection('cities').orderBy('name').get();
    setState(() {
      _cities = snap.docs.map((d) => _DropItem(d.id, d.get('name') as String? ?? d.id)).toList();
    });
  }

  Future<void> _loadOffices(String provinceId, String cityId) async {
    setState(() { _offices = []; _selectedOffice = null; });
    final snap = await _firestore.collection('provinces').doc(provinceId).collection('cities').doc(cityId).collection('offices').get();
    setState(() {
      _offices = snap.docs.map((d) => _DropItem(d.id, d.get('name') as String? ?? d.id)).toList();
    });
  }

  Future<void> _handleSignUp() async {
    final email = _emailController.text.trim();
    final password = _passwordController.text;
    final confirmPassword = _confirmPasswordController.text;
    final name = _nameController.text.trim();
    final phone = _phoneController.text.trim();

    if (email.isEmpty || password.isEmpty || name.isEmpty || phone.isEmpty) {
      setState(() => _error = '모든 항목을 입력해주세요.');
      return;
    }
    if (password != confirmPassword) {
      setState(() => _error = '비밀번호가 일치하지 않습니다.');
      return;
    }
    if (_selectedProvince == null || _selectedCity == null || _selectedOffice == null) {
      setState(() => _error = '시/도, 시/군/구, 사무실을 모두 선택해주세요.');
      return;
    }

    setState(() { _isLoading = true; _error = null; });

    try {
      // 1. Firebase Auth 계정 생성
      final credential = await FirebaseAuth.instance.createUserWithEmailAndPassword(
        email: email, password: password,
      );
      final uid = credential.user?.uid;
      if (uid == null) throw Exception('계정 생성 실패');

      // 2. pending_drivers 문서 생성
      await _firestore.collection('pending_drivers').doc(uid).set({
        'authUid': uid,
        'name': name,
        'phoneNumber': phone,
        'email': email,
        'driverType': '대리기사',
        'targetProvinceId': _selectedProvince!.id,
        'targetCityId': _selectedCity!.id,
        'targetOfficeId': _selectedOffice!.id,
        'status': '승인대기중',
        'requestedAt': FieldValue.serverTimestamp(),
      });

      // 3. 로그아웃 (승인 전까지 로그인 불가)
      await FirebaseAuth.instance.signOut();

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('가입 신청이 완료되었습니다. 관리자 승인을 기다려주세요.'), backgroundColor: Colors.green),
        );
        Navigator.pop(context);
      }
    } on FirebaseAuthException catch (e) {
      setState(() {
        if (e.code == 'email-already-in-use') {
          _error = '이미 사용 중인 이메일입니다.';
        } else if (e.code == 'weak-password') {
          _error = '비밀번호가 너무 약합니다. (6자 이상)';
        } else {
          _error = e.message ?? '회원가입 실패';
        }
      });
    } catch (e) {
      setState(() => _error = '회원가입 실패: $e');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('회원가입')),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              TextField(
                controller: _emailController, enabled: !_isLoading,
                decoration: const InputDecoration(labelText: '이메일'),
                keyboardType: TextInputType.emailAddress,
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _passwordController, enabled: !_isLoading,
                decoration: InputDecoration(
                  labelText: '비밀번호',
                  suffixIcon: IconButton(
                    icon: Icon(_obscurePassword ? Icons.visibility_off : Icons.visibility),
                    onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
                  ),
                ),
                obscureText: _obscurePassword,
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _confirmPasswordController, enabled: !_isLoading,
                decoration: InputDecoration(
                  labelText: '비밀번호 확인',
                  suffixIcon: IconButton(
                    icon: Icon(_obscureConfirm ? Icons.visibility_off : Icons.visibility),
                    onPressed: () => setState(() => _obscureConfirm = !_obscureConfirm),
                  ),
                ),
                obscureText: _obscureConfirm,
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _nameController, enabled: !_isLoading,
                decoration: const InputDecoration(labelText: '이름'),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _phoneController, enabled: !_isLoading,
                decoration: const InputDecoration(labelText: '전화번호'),
                keyboardType: TextInputType.phone,
              ),
              const SizedBox(height: 20),

              // 3단계 드롭다운
              _buildDropdown('시/도', _provinces, _selectedProvince, (v) {
                setState(() => _selectedProvince = v);
                if (v != null) _loadCities(v.id);
              }),
              const SizedBox(height: 12),
              _buildDropdown('시/군/구', _cities, _selectedCity, (v) {
                setState(() => _selectedCity = v);
                if (v != null && _selectedProvince != null) _loadOffices(_selectedProvince!.id, v.id);
              }),
              const SizedBox(height: 12),
              _buildDropdown('사무실', _offices, _selectedOffice, (v) {
                setState(() => _selectedOffice = v);
              }),
              const SizedBox(height: 20),

              if (_error != null)
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: Text(_error!, style: const TextStyle(color: Colors.red, fontSize: 14)),
                ),

              ElevatedButton(
                onPressed: _isLoading ? null : _handleSignUp,
                style: ElevatedButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 16)),
                child: _isLoading
                    ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
                    : const Text('가입 신청'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildDropdown(String label, List<_DropItem> items, _DropItem? selected, ValueChanged<_DropItem?> onChanged) {
    return DropdownButtonFormField<_DropItem>(
      value: selected,
      decoration: InputDecoration(labelText: label),
      items: items.map((e) => DropdownMenuItem(value: e, child: Text(e.name))).toList(),
      onChanged: _isLoading ? null : onChanged,
      isExpanded: true,
    );
  }
}

class _DropItem {
  final String id;
  final String name;
  const _DropItem(this.id, this.name);

  @override
  bool operator ==(Object other) => other is _DropItem && other.id == id;

  @override
  int get hashCode => id.hashCode;
}
