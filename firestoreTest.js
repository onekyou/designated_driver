// 1. Firebase Admin SDK 불러오기
const admin = require('firebase-admin');

// 2. 서비스 계정 키 파일 불러오기
//    여러분이 저장한 serviceAccountKey.json 파일의 경로를 정확히 지정합니다.
//    이 파일이 'designated_driver' 폴더 안에 있고, 그 안에 'credentials' 폴더가 있다면,
//    상대 경로로 './credentials/serviceAccountKey.json'이 됩니다.
const serviceAccount = require('./credentials/serviceAccountKey.json');

// 3. Firebase 앱 초기화
//    이 단계에서 불러온 서비스 계정 키를 사용하여 Firebase 프로젝트에 인증합니다.
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

// 4. Firestore 데이터베이스 객체 얻기
//    이제 'db' 객체를 통해 Firestore 데이터베이스와 상호작용할 수 있습니다.
const db = admin.firestore();

console.log('🎉 Firestore가 성공적으로 초기화되었습니다!');

// --- Firestore 데이터 작업 예시 ---

// 5. 데이터 추가하기 (예시: 'users' 컬렉션에 새 문서 추가)
async function addExampleUser() {
  try {
    const userRef = await db.collection('users').add({ // 'users'라는 컬렉션에 새 문서를 추가합니다.
      firstName: 'Alice',
      lastName: 'Smith',
      email: 'alice.smith@example.com',
      age: 28,
      createdAt: admin.firestore.FieldValue.serverTimestamp() // 서버 시간으로 생성 시간 기록
    });
    console.log(`✅ 사용자 데이터 추가 성공! 문서 ID: ${userRef.id}`);
  } catch (error) {
    console.error('❌ 사용자 데이터 추가 중 오류 발생:', error);
  }
}

// 6. 데이터 읽어오기 (예시: 'users' 컬렉션의 모든 문서 가져오기)
async function getExampleUsers() {
  try {
    const usersSnapshot = await db.collection('users').get(); // 'users' 컬렉션의 모든 문서를 가져옵니다.

    if (usersSnapshot.empty) {
      // 🐞 수정된 부분: 내부의 작은따옴표를 역슬래시로 이스케이프했습니다.
      console.log('ℹ️ \'users\' 컬렉션에 문서가 없습니다.'); 
      return;
    }  
    
    console.log('\n📝 현재 사용자 목록:');
    usersSnapshot.forEach(doc => {
      console.log(`${doc.id} => ${JSON.stringify(doc.data())}`); // 문서 ID와 데이터를 출력합니다.
    });
  } catch (error) {
    console.error('❌ 사용자 데이터 가져오기 중 오류 발생:', error);
  }
}

// 7. 함수 실행
//    이 두 함수를 호출하여 실제 Firestore 작업을 수행합니다.
//    먼저 데이터를 추가하고, 잠시 후 데이터를 가져오도록 설정했습니다.
addExampleUser();

// 데이터 추가 후 2초 대기 (데이터가 Firestore에 반영될 시간을 줌)
setTimeout(() => {
  getExampleUsers();
}, 2000);