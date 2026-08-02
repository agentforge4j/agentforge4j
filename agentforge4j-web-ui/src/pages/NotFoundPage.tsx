// SPDX-License-Identifier: Apache-2.0
import { Link } from 'react-router-dom';

export default function NotFoundPage() {
  return (
    <div className="flex flex-col items-center justify-center gap-4 py-24 text-center">
      <h1 className="text-2xl font-semibold text-fg">Page not found</h1>
      <Link to="/" className="text-brand underline">Go home</Link>
    </div>
  );
}
