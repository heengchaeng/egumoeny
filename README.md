# 🥚 Egumoney (에구머니)

**Egumoney**는 사용자의 지출 내역을 분석하여 객관적이고 친절한 피드백을 제공하는 스마트 가계부 애플리케이션입니다. Groq Cloud API(Llama 3.1)를 활용한 AI 분석을 통해 단순한 기록을 넘어 더 나은 소비 습관을 제안합니다.

<p align="center">
  <img src="app/src/main/res/drawable/app_logo.png" width="200" alt="Egumoney Logo">
</p>

---

## ✨ 주요 기능

### 1. 🤖 AI 소비 분석 (Groq API 활용)
- **객관적인 분석**: 지난달 동기 대비 지출 현황을 정밀하게 비교합니다.
- **맞춤형 예산 제안**: 현재 잔액을 바탕으로 오늘 하루 권장 소비 금액을 계산해 드립니다.
- **친절한 페르소나**: "에구머니!"라는 인사와 함께 존댓말로 정중하고 간결한 피드백을 제공합니다.

### 2. 📊 직관적인 대시보드
- **최상단 AI 리포트**: 앱을 열자마자 AI가 분석한 나의 소비 상태를 바로 확인할 수 있습니다.
- **파스텔 톤 상태 표시**: 예산 초과 시 `#FF8A8A`(Red), 안정권일 시 `#8AB6D6`(Blue) 색상을 적용하여 시각적 피로도를 낮췄습니다.
- **카테고리별 이모지**: 🍱 식비, 🚌 교통, 🛍️ 쇼핑 등 직관적인 이모지를 사용하여 한눈에 들어오는 목록을 제공합니다.

### 3. 📈 시각화 차트
- **파이 차트**: 카테고리별 지출 비중을 확인합니다.
- **막대 그래프**: 주간 지출 흐름을 파악하여 소비 패턴을 분석합니다.

### 4. ✍️ 스마트 입력
- **자연어 입력**: "오늘 친구랑 할리스에서 5000원 썼어"와 같이 일상 언어로 지출을 기록할 수 있습니다.
- **직접 입력**: 세부 사항을 직접 입력하여 정확한 기록이 가능합니다.

---

## 🛠 Tech Stack

- **Language**: Kotlin
- **Architecture**: MVVM (ViewModel, Repository, Room DB)
- **Network**: Retrofit2 (Groq Cloud API Integration)
- **AI Model**: `llama-3.1-8b-instant`
- **UI**: XML, Material Design, MPAndroidChart
- **Library**: Jetpack (Room, Lifecycle, ViewBinding)

---

## 🚀 시작하기

### API 키 설정
본 프로젝트는 **Groq Cloud API**를 사용합니다. `local.properties` 파일에 아래와 같이 API 키를 추가해야 합니다.

```properties
GROQ_API_KEY="YOUR_API_KEY_HERE"
```

### 설치 및 실행
1. 저장소를 클론합니다.
2. Android Studio에서 프로젝트를 엽니다.
3. Gradle Sync를 진행한 후 앱을 실행합니다.

---

## 🎨 UI Theme
- **Theme**: Black & White (Minimalism)
- **Accent Colors**: 
  - Over Budget: `#FF8A8A` (Pastel Red)
  - Under Budget: `#8AB6D6` (Pastel Blue)

---

## 📄 License
이 프로젝트는 개인 학습 및 포트폴리오 목적으로 제작되었습니다.
