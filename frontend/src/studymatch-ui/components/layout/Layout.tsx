
import { Button } from '../button/Button';
import './landing.css';

export function Navbar() {
  return (
    <header className="sm-navbar sm-glass">
      <nav className="sm-navbar__links sm-text">
        <a href="#home" className="sm-navbar__link">Home</a>
        <a href="#study-groups" className="sm-navbar__link">My Subjects</a>
        <a href="#study-groups" className="sm-navbar__link">Study Rooms</a>
      </nav>

      <Button variant="gradient" size="md">
        Get Started
      </Button>
    </header>
  );
}

export function Footer() {
  return (
    <footer className="sm-footer sm-glass">
      <div className="sm-text sm-muted">
        © {new Date().getFullYear()} StudyMatch. All rights reserved.
      </div>
    </footer>
  );
}