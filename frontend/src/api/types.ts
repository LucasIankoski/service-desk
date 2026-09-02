export type Role = "REQUESTER" | "AGENT" | "MANAGER" | "ADMIN";
export type TicketStatus =
  | "OPEN"
  | "TRIAGE"
  | "IN_PROGRESS"
  | "WAITING_REQUESTER"
  | "RESOLVED"
  | "CLOSED"
  | "CANCELED";
export type Priority = "LOW" | "NORMAL" | "HIGH" | "CRITICAL";
export type CommentVisibility = "PUBLIC" | "INTERNAL";
export type NotificationType =
  | "ASSIGNED"
  | "PUBLIC_COMMENT"
  | "MENTION"
  | "STATUS_CHANGED"
  | "DEADLINE_SOON"
  | "OVERDUE";
export type AttachmentScanStatus = "CLEAN" | "INFECTED" | "ERROR";

export type Page<T> = {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type ProblemDetail = {
  title?: string;
  detail?: string;
  status?: number;
  correlationId?: string;
  errors?: Record<string, string>;
};

export type User = {
  id: string;
  email: string;
  displayName: string;
  roles: Role[];
  active: boolean;
  passwordChangeRequired: boolean;
  anonymized: boolean;
  createdAt: string;
};

export type Me = Pick<User, "id" | "email" | "displayName" | "roles" | "passwordChangeRequired">;

export type Assignee = Pick<User, "id" | "displayName">;

export type Category = {
  id: string;
  name: string;
  active: boolean;
  createdAt: string;
};

export type Theme = {
  primaryColor: string;
  accentColor: string;
  sidebarColor: string;
  canvasColor: string;
};

export type PublicSettings = {
  institutionName: string;
  supportEmail?: string | null;
  supportPhone?: string | null;
  timezoneName: string;
  theme: Theme;
  loginBackgroundUrl?: string | null;
  version: number;
  updatedAt: string;
};

export type AdminSettings = PublicSettings & {
  attachmentLimitMb: number;
  reopenDays: number;
  deadlineWarningHours: number;
  loginBackgroundConfigured: boolean;
  smtp: {
    host?: string | null;
    port?: number | null;
    tls: boolean;
    fromName?: string | null;
    fromAddress?: string | null;
    username?: string | null;
    passwordConfigured: boolean;
  };
};

export type Attachment = {
  id: string;
  ticketId: string;
  commentId?: string | null;
  originalName: string;
  mediaType: string;
  fileSize: number;
  sha256: string;
  scanStatus: AttachmentScanStatus;
  visibility: CommentVisibility;
  createdAt: string;
};

export type TicketSummary = {
  id: string;
  publicNumber: string;
  subject: string;
  status: TicketStatus;
  priority: Priority;
  requesterId: string;
  requesterName?: string | null;
  assigneeId?: string | null;
  assigneeName?: string | null;
  categoryId?: string | null;
  categoryName?: string | null;
  dueAt?: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type TicketComment = {
  id: string;
  authorId: string;
  authorName?: string | null;
  body: string;
  visibility: CommentVisibility;
  createdAt: string;
  attachments: Attachment[];
};

export type TicketDetail = TicketSummary & {
  description: string;
  attachments: Attachment[];
  comments: TicketComment[];
};

export type Notification = {
  id: string;
  ticketId?: string | null;
  type: NotificationType;
  title: string;
  message: string;
  read: boolean;
  createdAt: string;
};
