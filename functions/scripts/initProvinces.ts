/**
 * Firestore provinces/cities 초기화 스크립트
 * 실행: cd functions && npx ts-node scripts/initProvinces.ts
 */

import * as admin from "firebase-admin";

// Firebase Admin 초기화
const serviceAccount = require("../service-account-key.json");
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
});

const db = admin.firestore();

// 지역 데이터 정의
interface ProvinceData {
  name: string;
  type: "do" | "metropolitan";
  active: boolean;
  cities: {
    id: string;
    name: string;
    active: boolean;
  }[];
}

const provinces: Record<string, ProvinceData> = {
  // ===== 광역시/특별자치시 (서울 제외) =====
  busan: {
    name: "부산광역시",
    type: "metropolitan",
    active: false,
    cities: [{ id: "busan", name: "부산", active: false }],
  },
  daegu: {
    name: "대구광역시",
    type: "metropolitan",
    active: false,
    cities: [{ id: "daegu", name: "대구", active: false }],
  },
  incheon: {
    name: "인천광역시",
    type: "metropolitan",
    active: false,
    cities: [{ id: "incheon", name: "인천", active: false }],
  },
  gwangju: {
    name: "광주광역시",
    type: "metropolitan",
    active: false,
    cities: [{ id: "gwangju", name: "광주", active: false }],
  },
  daejeon: {
    name: "대전광역시",
    type: "metropolitan",
    active: false,
    cities: [{ id: "daejeon", name: "대전", active: false }],
  },
  ulsan: {
    name: "울산광역시",
    type: "metropolitan",
    active: false,
    cities: [{ id: "ulsan", name: "울산", active: false }],
  },
  sejong: {
    name: "세종특별자치시",
    type: "metropolitan",
    active: false,
    cities: [{ id: "sejong", name: "세종", active: false }],
  },

  // ===== 도 =====
  gyeonggi: {
    name: "경기도",
    type: "do",
    active: true, // ✅ 활성화
    cities: [
      { id: "suwon", name: "수원시", active: false },
      { id: "seongnam", name: "성남시", active: false },
      { id: "goyang", name: "고양시", active: false },
      { id: "yongin", name: "용인시", active: false },
      { id: "bucheon", name: "부천시", active: false },
      { id: "ansan", name: "안산시", active: false },
      { id: "anyang", name: "안양시", active: false },
      { id: "namyangju", name: "남양주시", active: false },
      { id: "hwaseong", name: "화성시", active: false },
      { id: "uijeongbu", name: "의정부시", active: false },
      { id: "siheung", name: "시흥시", active: false },
      { id: "pyeongtaek", name: "평택시", active: false },
      { id: "gwangmyeong", name: "광명시", active: false },
      { id: "paju", name: "파주시", active: false },
      { id: "gunpo", name: "군포시", active: false },
      { id: "gwangju_city", name: "광주시", active: false },
      { id: "gimpo", name: "김포시", active: false },
      { id: "icheon", name: "이천시", active: false },
      { id: "yangju", name: "양주시", active: false },
      { id: "osan", name: "오산시", active: false },
      { id: "guri", name: "구리시", active: false },
      { id: "anseong", name: "안성시", active: false },
      { id: "pocheon", name: "포천시", active: false },
      { id: "uiwang", name: "의왕시", active: false },
      { id: "hanam", name: "하남시", active: false },
      { id: "yeoju", name: "여주시", active: false },
      { id: "yangpyeong", name: "양평군", active: true }, // ✅ 활성화
      { id: "dongducheon", name: "동두천시", active: false },
      { id: "gwacheon", name: "과천시", active: false },
      { id: "gapyeong", name: "가평군", active: false },
      { id: "yeoncheon", name: "연천군", active: false },
    ],
  },
  gangwon: {
    name: "강원특별자치도",
    type: "do",
    active: false,
    cities: [
      { id: "chuncheon", name: "춘천시", active: false },
      { id: "wonju", name: "원주시", active: false },
      { id: "gangneung", name: "강릉시", active: false },
      { id: "donghae", name: "동해시", active: false },
      { id: "taebaek", name: "태백시", active: false },
      { id: "sokcho", name: "속초시", active: false },
      { id: "samcheok", name: "삼척시", active: false },
      { id: "hongcheon", name: "홍천군", active: false },
      { id: "hoengseong", name: "횡성군", active: false },
      { id: "yeongwol", name: "영월군", active: false },
      { id: "pyeongchang", name: "평창군", active: false },
      { id: "jeongseon", name: "정선군", active: false },
      { id: "cheorwon", name: "철원군", active: false },
      { id: "hwacheon", name: "화천군", active: false },
      { id: "yanggu", name: "양구군", active: false },
      { id: "inje", name: "인제군", active: false },
      { id: "goseong", name: "고성군", active: false },
      { id: "yangyang", name: "양양군", active: false },
    ],
  },
  chungbuk: {
    name: "충청북도",
    type: "do",
    active: false,
    cities: [
      { id: "cheongju", name: "청주시", active: false },
      { id: "chungju", name: "충주시", active: false },
      { id: "jecheon", name: "제천시", active: false },
      { id: "boeun", name: "보은군", active: false },
      { id: "okcheon", name: "옥천군", active: false },
      { id: "yeongdong", name: "영동군", active: false },
      { id: "jeungpyeong", name: "증평군", active: false },
      { id: "jincheon", name: "진천군", active: false },
      { id: "goesan", name: "괴산군", active: false },
      { id: "eumseong", name: "음성군", active: false },
      { id: "danyang", name: "단양군", active: false },
    ],
  },
  chungnam: {
    name: "충청남도",
    type: "do",
    active: false,
    cities: [
      { id: "cheonan", name: "천안시", active: false },
      { id: "gongju", name: "공주시", active: false },
      { id: "boryeong", name: "보령시", active: false },
      { id: "asan", name: "아산시", active: false },
      { id: "seosan", name: "서산시", active: false },
      { id: "nonsan", name: "논산시", active: false },
      { id: "gyeryong", name: "계룡시", active: false },
      { id: "dangjin", name: "당진시", active: false },
      { id: "geumsan", name: "금산군", active: false },
      { id: "buyeo", name: "부여군", active: false },
      { id: "seocheon", name: "서천군", active: false },
      { id: "cheongyang", name: "청양군", active: false },
      { id: "hongseong", name: "홍성군", active: false },
      { id: "yesan", name: "예산군", active: false },
      { id: "taean", name: "태안군", active: false },
    ],
  },
  jeonbuk: {
    name: "전북특별자치도",
    type: "do",
    active: false,
    cities: [
      { id: "jeonju", name: "전주시", active: false },
      { id: "gunsan", name: "군산시", active: false },
      { id: "iksan", name: "익산시", active: false },
      { id: "jeongeup", name: "정읍시", active: false },
      { id: "namwon", name: "남원시", active: false },
      { id: "gimje", name: "김제시", active: false },
      { id: "wanju", name: "완주군", active: false },
      { id: "jinan", name: "진안군", active: false },
      { id: "muju", name: "무주군", active: false },
      { id: "jangsu", name: "장수군", active: false },
      { id: "imsil", name: "임실군", active: false },
      { id: "sunchang", name: "순창군", active: false },
      { id: "gochang", name: "고창군", active: false },
      { id: "buan", name: "부안군", active: false },
    ],
  },
  jeonnam: {
    name: "전라남도",
    type: "do",
    active: false,
    cities: [
      { id: "mokpo", name: "목포시", active: false },
      { id: "yeosu", name: "여수시", active: false },
      { id: "suncheon", name: "순천시", active: false },
      { id: "naju", name: "나주시", active: false },
      { id: "gwangyang", name: "광양시", active: false },
      { id: "damyang", name: "담양군", active: false },
      { id: "gokseong", name: "곡성군", active: false },
      { id: "gurye", name: "구례군", active: false },
      { id: "goheung", name: "고흥군", active: false },
      { id: "boseong", name: "보성군", active: false },
      { id: "hwasun", name: "화순군", active: false },
      { id: "jangheung", name: "장흥군", active: false },
      { id: "gangjin", name: "강진군", active: false },
      { id: "haenam", name: "해남군", active: false },
      { id: "yeongam", name: "영암군", active: false },
      { id: "muan", name: "무안군", active: false },
      { id: "hampyeong", name: "함평군", active: false },
      { id: "yeonggwang", name: "영광군", active: false },
      { id: "jangseong", name: "장성군", active: false },
      { id: "wando", name: "완도군", active: false },
      { id: "jindo", name: "진도군", active: false },
      { id: "sinan", name: "신안군", active: false },
    ],
  },
  gyeongbuk: {
    name: "경상북도",
    type: "do",
    active: false,
    cities: [
      { id: "pohang", name: "포항시", active: false },
      { id: "gyeongju", name: "경주시", active: false },
      { id: "gimcheon", name: "김천시", active: false },
      { id: "andong", name: "안동시", active: false },
      { id: "gumi", name: "구미시", active: false },
      { id: "yeongju", name: "영주시", active: false },
      { id: "yeongcheon", name: "영천시", active: false },
      { id: "sangju", name: "상주시", active: false },
      { id: "mungyeong", name: "문경시", active: false },
      { id: "gyeongsan", name: "경산시", active: false },
      { id: "uiseong", name: "의성군", active: false },
      { id: "cheongsong", name: "청송군", active: false },
      { id: "yeongyang", name: "영양군", active: false },
      { id: "yeongdeok", name: "영덕군", active: false },
      { id: "cheongdo", name: "청도군", active: false },
      { id: "goryeong", name: "고령군", active: false },
      { id: "seongju", name: "성주군", active: false },
      { id: "chilgok", name: "칠곡군", active: false },
      { id: "yecheon", name: "예천군", active: false },
      { id: "bonghwa", name: "봉화군", active: false },
      { id: "uljin", name: "울진군", active: false },
      { id: "ulleung", name: "울릉군", active: false },
    ],
  },
  gyeongnam: {
    name: "경상남도",
    type: "do",
    active: false,
    cities: [
      { id: "changwon", name: "창원시", active: false },
      { id: "jinju", name: "진주시", active: false },
      { id: "tongyeong", name: "통영시", active: false },
      { id: "sacheon", name: "사천시", active: false },
      { id: "gimhae", name: "김해시", active: false },
      { id: "miryang", name: "밀양시", active: false },
      { id: "geoje", name: "거제시", active: false },
      { id: "yangsan", name: "양산시", active: false },
      { id: "uiryeong", name: "의령군", active: false },
      { id: "haman", name: "함안군", active: false },
      { id: "changnyeong", name: "창녕군", active: false },
      { id: "goseong_gn", name: "고성군", active: false },
      { id: "namhae", name: "남해군", active: false },
      { id: "hadong", name: "하동군", active: false },
      { id: "sancheong", name: "산청군", active: false },
      { id: "hamyang", name: "함양군", active: false },
      { id: "geochang", name: "거창군", active: false },
      { id: "hapcheon", name: "합천군", active: false },
    ],
  },
  jeju: {
    name: "제주특별자치도",
    type: "do",
    active: false,
    cities: [
      { id: "jeju_city", name: "제주시", active: false },
      { id: "seogwipo", name: "서귀포시", active: false },
    ],
  },
};

async function initProvinces() {
  console.log("🚀 Firestore provinces/cities 초기화 시작...\n");

  const batch = db.batch();
  let provinceCount = 0;
  let cityCount = 0;

  for (const [provinceId, data] of Object.entries(provinces)) {
    // Province 문서 생성
    const provinceRef = db.collection("provinces").doc(provinceId);
    batch.set(provinceRef, {
      name: data.name,
      type: data.type,
      active: data.active,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    provinceCount++;

    const status = data.active ? "✅" : "⬚";
    console.log(`${status} ${data.name} (${provinceId}) - type: ${data.type}`);

    // Cities 문서 생성
    for (const city of data.cities) {
      const cityRef = provinceRef.collection("cities").doc(city.id);
      batch.set(cityRef, {
        name: city.name,
        active: city.active,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      cityCount++;

      const cityStatus = city.active ? "  ✅" : "  ⬚";
      console.log(`${cityStatus} ${city.name} (${city.id})`);
    }
    console.log("");
  }

  // Batch 커밋
  await batch.commit();

  console.log("========================================");
  console.log(`✅ 완료! Province: ${provinceCount}개, City: ${cityCount}개`);
  console.log("========================================");
}

// 실행
initProvinces()
  .then(() => {
    console.log("\n🎉 초기화 완료!");
    process.exit(0);
  })
  .catch((error) => {
    console.error("❌ 오류 발생:", error);
    process.exit(1);
  });
