import { ArrowLeft, LifeBuoy } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link, Route, Routes } from 'react-router-dom';
import { MyTicketsPage } from './MyTicketsPage';
import { TicketPage } from './TicketPage';
import './help.css';

// on the personality test page
export function HelpLink() {
  const { t } = useTranslation();
  return <Link to="/help"><LifeBuoy size={16} aria-hidden /> {t('help.needHelp')}</Link>;
}

// a student who hasn't taken the personality test has no menu yet, but can still ask for help (#363)
export function HelpBeforeTest({ me, myName }: { me: number; myName: string }) {
  const { t } = useTranslation();
  return (
    <main className="personality-page">
      <div className="help-standalone">
        <Link to="/" className="help-ticket__back help-standalone__back"><ArrowLeft size={16} aria-hidden /> {t('help.backToTest')}</Link>
        <Routes>
          <Route index element={<MyTicketsPage />} />
          <Route path=":ticketId" element={<TicketPage me={me} myName={myName} />} />
        </Routes>
      </div>
    </main>
  );
}
