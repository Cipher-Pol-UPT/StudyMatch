import './avatar.css';

export const AVATAR_GRADIENTS = [
  'linear-gradient(135deg, #8f82ff, #4a3df5)',
  'linear-gradient(135deg, #6cc6f5, #2a7fd6)',
  'linear-gradient(135deg, #ffad7a, #eb6834)',
  'linear-gradient(135deg, #5fe0ad, #139e6c)',
  'linear-gradient(135deg, #ff8fbf, #d4498b)',
  'linear-gradient(135deg, #b5a6ff, #7a4cf0)',
  'linear-gradient(135deg, #4b4b5a, #111118)',
];

export function initialsOf(name: string) {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  return ((parts[0]?.[0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
}
