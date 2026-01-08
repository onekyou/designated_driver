# 기술 스택 및 Dependencies 상세

## 🛠️ 핵심 기술 스택

### React Native Core
```json
{
  "react": "18.2.0",
  "react-native": "0.73.2",
  "typescript": "5.3.3"
}
```

### 개발 도구
```json
{
  "@types/react": "18.2.48",
  "@types/react-native": "0.72.8",
  "@typescript-eslint/eslint-plugin": "6.19.0",
  "@typescript-eslint/parser": "6.19.0",
  "eslint": "8.56.0",
  "eslint-config-prettier": "9.1.0",
  "eslint-plugin-react": "7.33.2",
  "eslint-plugin-react-hooks": "4.6.0",
  "prettier": "3.2.4"
}
```

---

## 📱 주요 라이브러리

### 1. 네비게이션
```json
{
  "@react-navigation/native": "^6.1.9",
  "@react-navigation/native-stack": "^6.9.17",
  "@react-navigation/bottom-tabs": "^6.5.11",
  "@react-navigation/drawer": "^6.6.6",
  "react-native-screens": "^3.29.0",
  "react-native-safe-area-context": "^4.8.2",
  "react-native-gesture-handler": "^2.14.1",
  "react-native-reanimated": "^3.6.1"
}
```

### 2. 상태 관리
```json
{
  "@reduxjs/toolkit": "^2.0.1",
  "react-redux": "^9.1.0",
  "redux-persist": "^6.0.0",
  "@react-native-async-storage/async-storage": "^1.21.0"
}
```

### 3. Firebase
```json
{
  "@react-native-firebase/app": "^18.7.3",
  "@react-native-firebase/auth": "^18.7.3",
  "@react-native-firebase/firestore": "^18.7.3",
  "@react-native-firebase/messaging": "^18.7.3",
  "@react-native-firebase/crashlytics": "^18.7.3",
  "@react-native-firebase/analytics": "^18.7.3"
}
```

### 4. UI 컴포넌트
```json
{
  "react-native-paper": "^5.12.3",
  "react-native-vector-icons": "^10.0.3",
  "react-native-modal": "^13.0.1",
  "react-native-toast-message": "^2.2.0",
  "react-native-loading-spinner-overlay": "^3.0.1"
}
```

### 5. 푸시 알림
```json
{
  "@notifee/react-native": "^7.8.2",
  "react-native-push-notification": "^8.1.1",
  "@react-native-community/push-notification-ios": "^1.11.0"
}
```

### 6. 위치 서비스
```json
{
  "@react-native-community/geolocation": "^3.1.0",
  "react-native-geocoding": "^0.5.0",
  "react-native-maps": "^1.8.4",
  "react-native-google-places-autocomplete": "^2.5.6"
}
```

### 7. 백그라운드 서비스
```json
{
  "react-native-background-job": "^3.0.8",
  "react-native-background-fetch": "^4.2.1",
  "react-native-background-geolocation": "^4.14.3"
}
```

### 8. 폼 처리
```json
{
  "react-hook-form": "^7.49.3",
  "yup": "^1.3.3"
}
```

### 9. 네트워크
```json
{
  "axios": "^1.6.5",
  "react-native-netinfo": "^11.2.1"
}
```

### 10. 유틸리티
```json
{
  "lodash": "^4.17.21",
  "moment": "^2.30.1",
  "react-native-uuid": "^2.0.1",
  "react-native-config": "^1.5.1"
}
```

---

## 📦 package.json 전체 구조

```json
{
  "name": "DriverAppRN",
  "version": "1.0.0",
  "private": true,
  "scripts": {
    "android": "react-native run-android",
    "ios": "react-native run-ios",
    "start": "react-native start",
    "test": "jest",
    "lint": "eslint . --ext .js,.jsx,.ts,.tsx",
    "format": "prettier --write \"src/**/*.{ts,tsx,js,jsx,json}\"",
    "type-check": "tsc --noEmit",
    "pod-install": "cd ios && pod install",
    "clean": "watchman watch-del-all && rm -rf node_modules && npm install",
    "clean:android": "cd android && ./gradlew clean",
    "clean:ios": "cd ios && xcodebuild clean"
  },
  "dependencies": {
    "react": "18.2.0",
    "react-native": "0.73.2",
    "@react-navigation/native": "^6.1.9",
    "@react-navigation/native-stack": "^6.9.17",
    "@react-navigation/bottom-tabs": "^6.5.11",
    "@reduxjs/toolkit": "^2.0.1",
    "react-redux": "^9.1.0",
    "@react-native-firebase/app": "^18.7.3",
    "@react-native-firebase/auth": "^18.7.3",
    "@react-native-firebase/firestore": "^18.7.3",
    "@react-native-firebase/messaging": "^18.7.3",
    "react-native-paper": "^5.12.3",
    "@notifee/react-native": "^7.8.2",
    "@react-native-community/geolocation": "^3.1.0",
    "react-hook-form": "^7.49.3",
    "axios": "^1.6.5"
  },
  "devDependencies": {
    "@babel/core": "^7.23.7",
    "@babel/preset-env": "^7.23.7",
    "@babel/runtime": "^7.23.7",
    "@react-native/babel-preset": "^0.73.19",
    "@react-native/eslint-config": "^0.73.2",
    "@react-native/metro-config": "^0.73.3",
    "@react-native/typescript-config": "^0.73.1",
    "@types/jest": "^29.5.11",
    "@types/react": "^18.2.48",
    "@types/react-native": "^0.72.8",
    "babel-jest": "^29.7.0",
    "eslint": "^8.56.0",
    "jest": "^29.7.0",
    "prettier": "^3.2.4",
    "react-test-renderer": "18.2.0",
    "typescript": "^5.3.3"
  },
  "engines": {
    "node": ">=18",
    "npm": ">=9"
  }
}
```

---

## 🔧 네이티브 설정

### Android 설정 (android/app/build.gradle)
```gradle
android {
    compileSdkVersion 34

    defaultConfig {
        applicationId "com.designated.driverapp"
        minSdkVersion 23
        targetSdkVersion 34
        versionCode 1
        versionName "1.0.0"
        multiDexEnabled true
    }

    signingConfigs {
        release {
            // 서명 설정
        }
    }

    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
        }
    }
}

dependencies {
    implementation 'com.facebook.react:react-native:+'
    implementation 'androidx.multidex:multidex:2.0.1'
}
```

### iOS 설정 (ios/Podfile)
```ruby
require_relative '../node_modules/react-native/scripts/react_native_pods'
require_relative '../node_modules/@react-native-community/cli-platform-ios/native_modules'

platform :ios, '13.0'
install! 'cocoapods', :deterministic_uuids => false

target 'DriverApp' do
  config = use_native_modules!

  use_react_native!(
    :path => config[:reactNativePath],
    :hermes_enabled => true,
    :fabric_enabled => false,
    :app_path => "#{Pod::Config.instance.installation_root}/.."
  )

  # Firebase
  use_frameworks! :linkage => :static

  # 권한 설정
  permissions_path = '../node_modules/react-native-permissions/ios'
  pod 'Permission-LocationWhenInUse', :path => "#{permissions_path}/LocationWhenInUse"
  pod 'Permission-Notifications', :path => "#{permissions_path}/Notifications"

  post_install do |installer|
    react_native_post_install(installer)
  end
end
```

---

## 🌐 환경 변수 설정

### .env 파일 구조
```bash
# Firebase Configuration
FIREBASE_API_KEY=your_api_key_here
FIREBASE_AUTH_DOMAIN=your_auth_domain_here
FIREBASE_PROJECT_ID=your_project_id_here
FIREBASE_STORAGE_BUCKET=your_storage_bucket_here
FIREBASE_MESSAGING_SENDER_ID=your_sender_id_here
FIREBASE_APP_ID=your_app_id_here

# API Endpoints
API_BASE_URL=https://api.designated-driver.com
API_TIMEOUT=30000

# Google Maps
GOOGLE_MAPS_API_KEY=your_google_maps_key_here

# App Configuration
APP_NAME=DriverApp
APP_VERSION=1.0.0
APP_BUILD=1

# Environment
ENV=development
DEBUG_MODE=true
```

---

## 📊 의존성 관리 전략

### 버전 관리 원칙
1. **Major 버전**: 안정성이 검증된 최신 메이저 버전 사용
2. **Minor 버전**: 자동 업데이트 허용 (^)
3. **Patch 버전**: 자동 업데이트 허용
4. **Critical 패키지**: 정확한 버전 고정

### 업데이트 주기
- **보안 패치**: 즉시 적용
- **Minor 업데이트**: 월 1회 검토
- **Major 업데이트**: 분기별 검토

### 의존성 감사
```bash
# 보안 취약점 확인
npm audit

# 자동 수정
npm audit fix

# 의존성 업데이트 확인
npm outdated
```

---

## 🔐 보안 고려사항

### API 키 보호
1. 환경 변수로 관리
2. .gitignore에 .env 파일 추가
3. 프로덕션 빌드 시 난독화

### 네이티브 레벨 보안
```java
// Android: AndroidManifest.xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="${GOOGLE_MAPS_API_KEY}" />
```

```swift
// iOS: Info.plist
<key>GOOGLE_MAPS_API_KEY</key>
<string>$(GOOGLE_MAPS_API_KEY)</string>
```

---

## 📈 성능 최적화 설정

### Metro 설정 (metro.config.js)
```javascript
module.exports = {
  transformer: {
    minifierConfig: {
      keep_fnames: true,
      mangle: {
        keep_fnames: true,
      },
    },
  },
  resolver: {
    sourceExts: ['jsx', 'js', 'ts', 'tsx', 'json'],
  },
};
```

### Babel 설정 (babel.config.js)
```javascript
module.exports = {
  presets: ['module:@react-native/babel-preset'],
  plugins: [
    'react-native-reanimated/plugin',
    [
      'module-resolver',
      {
        root: ['./src'],
        alias: {
          '@components': './src/components',
          '@screens': './src/screens',
          '@services': './src/services',
          '@store': './src/store',
          '@utils': './src/utils',
        },
      },
    ],
  ],
};
```

---

## 🧪 테스트 설정

### Jest 설정 (jest.config.js)
```javascript
module.exports = {
  preset: 'react-native',
  moduleFileExtensions: ['ts', 'tsx', 'js', 'jsx', 'json', 'node'],
  transformIgnorePatterns: [
    'node_modules/(?!(react-native|@react-native|@react-navigation)/)',
  ],
  setupFilesAfterEnv: ['<rootDir>/jest.setup.js'],
  moduleNameMapper: {
    '^@/(.*)$': '<rootDir>/src/$1',
  },
  testMatch: ['**/__tests__/**/*.(ts|tsx|js)', '**/*.(test|spec).(ts|tsx|js)'],
  collectCoverageFrom: [
    'src/**/*.{ts,tsx}',
    '!src/**/*.d.ts',
  ],
};
```