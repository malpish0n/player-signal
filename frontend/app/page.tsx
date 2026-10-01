import { GameSetup } from "./game-setup";
export default function Home() {
  return <>
    <div className="eyebrow">STEAM REVIEWS / LOCAL ALPHA</div>
    <h1>Start with what players are saying.</h1>
    <p className="intro">Connect a Steam game, import its public reviews and explore the original feedback. Every review stays linked to its source ID.</p>
    <GameSetup />
  </>;
}
