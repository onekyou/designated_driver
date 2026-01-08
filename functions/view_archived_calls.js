const admin = require('firebase-admin');

// Firebase Admin SDK 초기화
const serviceAccount = require('./service-account-key.json');
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  storageBucket: 'calldetector-5d61e.appspot.com' // Firebase Storage 버킷
});

const bucket = admin.storage().bucket();

/**
 * 아카이브된 콜 데이터 조회
 *
 * 사용법:
 * node view_archived_calls.js                           // 전체 아카이브 목록
 * node view_archived_calls.js Hongchon office1          // 특정 사무실 아카이브
 * node view_archived_calls.js Hongchon office1 2025-11-16  // 특정 날짜 아카이브
 */
async function viewArchivedCalls() {
  const args = process.argv.slice(2);
  const regionId = args[0];
  const officeId = args[1];
  const date = args[2];

  console.log('📦 아카이브 데이터 조회...\n');

  try {
    // 아카이브 파일 목록 조회
    let prefix = 'archives/calls/';
    if (regionId && officeId) {
      prefix = `archives/calls/${regionId}/${officeId}/`;
      if (date) {
        prefix = `archives/calls/${regionId}/${officeId}/completed_${date}.jsonl`;
      }
    }

    const [files] = await bucket.getFiles({ prefix });

    if (files.length === 0) {
      console.log('❌ 아카이브 파일이 없습니다.');
      console.log(`   경로: ${prefix}`);
      process.exit(0);
    }

    console.log(`✅ 총 ${files.length}개의 아카이브 파일 발견\n`);

    // 파일 목록만 보기
    if (!date) {
      console.log('📁 아카이브 파일 목록:');
      files.forEach(file => {
        console.log(`   ${file.name}`);
      });
      console.log('\n💡 특정 파일을 보려면: node view_archived_calls.js [regionId] [officeId] [YYYY-MM-DD]');
      process.exit(0);
    }

    // 특정 파일 내용 읽기
    const file = files[0];
    console.log(`📄 파일: ${file.name}\n`);

    const [contents] = await file.download();
    const lines = contents.toString().split('\n').filter(line => line.trim());

    console.log(`총 ${lines.length}개의 콜 데이터\n`);

    // JSON Lines 파싱 및 표시
    const calls = lines.map(line => JSON.parse(line));

    // 통계
    const stats = {
      total: calls.length,
      totalFare: 0,
      totalDriverFee: 0,
      totalCommission: 0
    };

    calls.forEach(call => {
      stats.totalFare += call.fare || 0;
      stats.totalDriverFee += call.driverFee || 0;
      stats.totalCommission += call.commissionFee || 0;
    });

    console.log('📊 통계:');
    console.log(`   총 콜 수: ${stats.total}개`);
    console.log(`   총 요금: ${stats.totalFare.toLocaleString()}원`);
    console.log(`   총 기사 수익: ${stats.totalDriverFee.toLocaleString()}원`);
    console.log(`   총 수수료: ${stats.totalCommission.toLocaleString()}원`);

    // 최근 10개 콜 샘플 표시
    console.log('\n📋 최근 10개 콜 샘플:');
    calls.slice(0, 10).forEach((call, index) => {
      console.log(`\n${index + 1}. 콜 ID: ${call.id}`);
      console.log(`   고객: ${call.customerName || 'N/A'} (${call.phoneNumber || 'N/A'})`);
      console.log(`   기사: ${call.assignedDriverName || 'N/A'}`);
      console.log(`   출발: ${call.pickupAddress || 'N/A'}`);
      console.log(`   도착: ${call.dropoffAddress || 'N/A'}`);
      console.log(`   요금: ${(call.fare || 0).toLocaleString()}원`);
      console.log(`   완료 시간: ${call.completedAt ? new Date(call.completedAt._seconds * 1000).toLocaleString('ko-KR') : 'N/A'}`);
    });

    if (calls.length > 10) {
      console.log(`\n... 외 ${calls.length - 10}개`);
    }

  } catch (error) {
    console.error('❌ 오류:', error);
  }

  process.exit(0);
}

viewArchivedCalls();
