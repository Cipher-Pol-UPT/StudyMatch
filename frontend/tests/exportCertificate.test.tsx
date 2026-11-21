import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { exportCertificate, verifyCertificate } from '../src/api/certificates';
import i18n from '../src/i18n';
import { AchievementsPage } from '../src/pages/achievements/AchievementsPage';
import { VerifyCertificatePage } from '../src/pages/verify/VerifyCertificatePage';
import { StudyMatchProvider, type StudentProfile } from '../src/studymatch-ui';

vi.mock('../src/api/certificates', () => ({
  exportCertificate: vi.fn(),
  verifyCertificate: vi.fn(),
}));

const profile: StudentProfile = {
  name: 'Test Tutor', number: 50003, degree: 'Test Degree', curricularYear: 3, languages: ['PORTUGUESE'],
  achievements: [{ id: 'FIRST_STUDYROOM', earnedAt: '2026-11-08' }, { id: 'MENTOR', earnedAt: '2026-11-12' }],
} as StudentProfile;

const exportDialog = () => screen.getByRole('dialog', { name: 'Export certificate' });

function renderAchievements() {
  render(<StudyMatchProvider><MemoryRouter><AchievementsPage profile={profile} failed={false} /></MemoryRouter></StudyMatchProvider>);
}

function renderVerify(code: string) {
  render(
    <StudyMatchProvider>
      <MemoryRouter initialEntries={[`/verify/${code}`]}>
        <Routes><Route path="/verify/:code" element={<VerifyCertificatePage />} /></Routes>
      </MemoryRouter>
    </StudyMatchProvider>,
  );
}

// from the achievement's own dialog, so only that one is ticked
async function exportFrom(name: RegExp) {
  fireEvent.click(screen.getByRole('button', { name }));
  fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Export certificate' }));
  return exportDialog();
}

afterEach(() => {
  cleanup();
  vi.mocked(exportCertificate).mockReset();
  vi.mocked(verifyCertificate).mockReset();
});
beforeEach(async () => {
  await i18n.changeLanguage('en');
  URL.createObjectURL = vi.fn(() => 'blob:test');
  URL.revokeObjectURL = vi.fn();
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
});

describe('export certificate', () => {
  it('exports the achievement it was opened from, in the current language', async () => {
    vi.mocked(exportCertificate).mockResolvedValue(new Blob(['%PDF-']));
    renderAchievements();
    const dialog = await exportFrom(/Mentor/);

    expect(within(dialog).getByRole('checkbox', { name: /Mentor/ })).toHaveProperty('checked', true);
    expect(within(dialog).getByRole('checkbox', { name: /First StudyRoom/ })).toHaveProperty('checked', false);
    fireEvent.click(within(dialog).getByRole('button', { name: 'Download PDF' }));

    await waitFor(() => expect(exportCertificate).toHaveBeenCalledWith({ achievements: ['MENTOR'], language: 'en' }));
    expect(await screen.findByText('Certificate downloaded.')).toBeTruthy();
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Export certificate' })).toBeNull());
  });

  it('exports every earned achievement from the page, as one record in Portuguese', async () => {
    vi.mocked(exportCertificate).mockResolvedValue(new Blob(['%PDF-']));
    renderAchievements();
    fireEvent.click(screen.getByRole('button', { name: 'Export certificate' }));
    const dialog = exportDialog();

    fireEvent.click(within(dialog).getByRole('radio', { name: 'Portuguese' }));
    fireEvent.click(within(dialog).getByRole('button', { name: 'Download PDF' }));

    await waitFor(() => expect(exportCertificate).toHaveBeenCalledWith({ achievements: ['FIRST_STUDYROOM', 'MENTOR'], language: 'pt' }));
  });

  it("can't download with no achievement chosen", async () => {
    renderAchievements();
    const dialog = await exportFrom(/Mentor/);

    fireEvent.click(within(dialog).getByRole('checkbox', { name: /Mentor/ }));

    expect(within(dialog).getByText('Choose at least one achievement.')).toBeTruthy();
    expect(within(dialog).getByRole('button', { name: 'Download PDF' })).toHaveProperty('disabled', true);
  });

  it('shows the error when the achievement is not earned, and keeps the dialog open', async () => {
    vi.mocked(exportCertificate).mockRejectedValue({ code: 'ACHIEVEMENT_NOT_EARNED', params: { achievement: 'MENTOR' } });
    renderAchievements();
    const dialog = await exportFrom(/Mentor/);

    fireEvent.click(within(dialog).getByRole('button', { name: 'Download PDF' }));

    expect(await within(dialog).findByText("You haven't earned one of these achievements yet.")).toBeTruthy();
    expect(screen.queryByText('Certificate downloaded.')).toBeNull();
  });
});

describe('verify certificate', () => {
  it('shows who earned the achievement, when, and the figures saved with it', async () => {
    vi.mocked(verifyCertificate).mockResolvedValue({
      record: false, studentName: 'Test Tutor', studentNumber: 50003, degree: 'Test Degree', issuedAt: '2026-11-20',
      achievements: [{
        achievement: 'MENTOR', earnedAt: '2026-11-12T10:00:00',
        evidence: { roomsHeld: 12, studentsTutored: 15, averageRating: 4.8, subject: 'Test Maths', recordedAt: '2026-11-20' },
      }],
    });
    renderVerify('test-code');

    expect(await screen.findByText('Valid certificate')).toBeTruthy();
    expect(verifyCertificate).toHaveBeenCalledWith('test-code');
    expect(screen.getByRole('heading', { name: 'Test Tutor' })).toBeTruthy();
    expect(screen.getByText('Student no. 50003 · Test Degree')).toBeTruthy();
    expect(screen.getByText('Issued by StudyMatch on November 20, 2026')).toBeTruthy();
    expect(screen.getByText('Mentor')).toBeTruthy();
    expect(screen.getByText('12 StudyRooms held · 15 students tutored · 4.8 average in Test Maths')).toBeTruthy();
  });

  it('says when a record is valid, with each achievement on it', async () => {
    vi.mocked(verifyCertificate).mockResolvedValue({
      record: true, studentName: 'Test Tutor', studentNumber: 50003, degree: null, issuedAt: '2026-11-20',
      achievements: [
        { achievement: 'FIRST_STUDYROOM', earnedAt: '2026-11-08T10:00:00', evidence: null },
        { achievement: 'MENTOR', earnedAt: '2026-11-12T10:00:00', evidence: null },
      ],
    });
    renderVerify('test-record');

    expect(await screen.findByText('Valid record of achievements')).toBeTruthy();
    expect(screen.getByText('Student no. 50003')).toBeTruthy();
    expect(screen.getAllByRole('listitem')).toHaveLength(2);
  });

  it("doesn't call the certificate invalid when the backend fails, and can try again", async () => {
    vi.mocked(verifyCertificate)
      .mockRejectedValueOnce(new Error('GET /achievements/certificates/test-code failed with status 503'))
      .mockResolvedValueOnce({
        record: false, studentName: 'Test Tutor', studentNumber: 50003, degree: null, issuedAt: '2026-11-20',
        achievements: [{ achievement: 'MENTOR', earnedAt: '2026-11-12T10:00:00', evidence: null }],
      });
    renderVerify('test-code');

    expect(await screen.findByText('Something went wrong. Please try again.')).toBeTruthy();
    expect(screen.queryByText("We couldn't find this certificate")).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }));

    expect(await screen.findByText('Valid certificate')).toBeTruthy();
    expect(verifyCertificate).toHaveBeenCalledTimes(2);
  });

  it("says when the code doesn't belong to any certificate", async () => {
    vi.mocked(verifyCertificate).mockRejectedValue({ code: 'NOT_FOUND' });
    renderVerify('not-a-code');

    expect(await screen.findByText("We couldn't find this certificate")).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Go to StudyMatch' }).getAttribute('href')).toBe('/');
  });
});
