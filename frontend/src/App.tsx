import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { TopBar } from './components/TopBar';
import { Landing } from './pages/Landing';
import { ManageTracking } from './pages/ManageTracking';
import { PlayerShell } from './pages/PlayerShell';
import { Overview } from './pages/Overview';
import { Champions } from './pages/Champions';
import { ChampionDetail } from './pages/ChampionDetail';
import { Players } from './pages/Players';
import { HeadToHead } from './pages/HeadToHead';
import { Trends } from './pages/Trends';
import { Matches } from './pages/Matches';
import { MatchDetail } from './pages/MatchDetail';
import { Live } from './pages/Live';

export default function App() {
  return (
    <BrowserRouter>
      <div className="app-shell">
        <TopBar />
        <main className="container page" style={{ flex: 1 }}>
          <Routes>
            <Route path="/" element={<Landing />} />
            <Route path="/tracked" element={<ManageTracking />} />
            <Route path="/player/:gameName/:tagLine" element={<PlayerShell />}>
              <Route index element={<Overview />} />
              <Route path="champions" element={<Champions />} />
              <Route path="champions/:championName" element={<ChampionDetail />} />
              <Route path="players" element={<Players />} />
              <Route path="players/:oppGameName/:oppTagLine" element={<HeadToHead />} />
              <Route path="trends" element={<Trends />} />
              <Route path="matches" element={<Matches />} />
              <Route path="matches/:matchId" element={<MatchDetail />} />
              <Route path="live" element={<Live />} />
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </main>
        <footer className="footer">
          Riot Analizer · unofficial · not endorsed by Riot Games · Champion & item art via Data Dragon
        </footer>
      </div>
    </BrowserRouter>
  );
}
